-- Wish photos: a wish may name one photo, kept like gift and person photos in the private
-- gift-photos bucket at "<owner>/<photo id>.jpg". The owner reads and writes it through the
-- existing own-folder policies. A member of a shared list may also read, and only read, the photo
-- of a wish that is on that list now.
--
-- Nothing else becomes readable. Membership of a list never opens the owner's folder: only an
-- object a live wish of that list names, while the member is still a member and neither the wish
-- nor the list is deleted. Gift photos, person photos and other lists' wishes stay owner-only.
-- No reservation data is involved.

-- ---------------------------------------------------------------------------------------------
-- Wish rows: a photo path only in the owner's folder, as for gifts and people
-- ---------------------------------------------------------------------------------------------

-- The app has never written a wish photo, so every existing row has none and the check holds.
alter table public.wishes
    add constraint wishes_photo_path_owned check (
        photo_path is null
        or photo_path ~ ('^' || owner_user_id::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$')
    );

-- Storage asks by object name on every shared read.
create index wishes_photo_path_idx on public.wishes (photo_path) where photo_path is not null;

-- ---------------------------------------------------------------------------------------------
-- Members' read access
-- ---------------------------------------------------------------------------------------------

-- Whether the signed-in user may read the gift-photos object [p_name] as a member of a list that
-- shows it. Security definer, because wishes and memberships are not readable by members; it answers
-- only yes or no, and only about the caller. Owners are answered by the own-folder policy instead.
create function public.can_read_shared_wish_photo(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.wishes w
        join public.wishlists l on l.id = w.wishlist_id and l.owner_user_id = w.owner_user_id
        join public.wishlist_members m on m.wishlist_id = l.id
        where w.photo_path = p_name
          and w.deleted_at is null
          and l.deleted_at is null
          and m.member_user_id = (select auth.uid())
    );
$$;

revoke all on function public.can_read_shared_wish_photo(text) from public, anon, authenticated, service_role;
grant execute on function public.can_read_shared_wish_photo(text) to authenticated;

-- Select only: members can never upload, replace or delete an owner's object.
create policy wish_photos_select_shared on storage.objects
    for select to authenticated
    using (bucket_id = 'gift-photos' and public.can_read_shared_wish_photo(name));

-- ---------------------------------------------------------------------------------------------
-- The member's view of a shared list names each wish's photo
-- ---------------------------------------------------------------------------------------------

-- The body is 20261105100000's with photo_path added; create or replace keeps the function's owner
-- and privileges (authenticated only).
create or replace function public.get_shared_wishlist(p_wishlist_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid      uuid := auth.uid();
    v_owner_id uuid;
    v_name     text;
    v_person   uuid;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    select l.owner_user_id, l.name, l.person_id into v_owner_id, v_name, v_person
    from public.wishlists l
    where l.id = p_wishlist_id and l.deleted_at is null;
    -- The owner is turned away here, before reservations are looked at.
    if not found or v_owner_id = v_uid then
        raise exception 'not available' using errcode = 'P0002';
    end if;
    if not exists (select 1 from public.wishlist_members m where m.wishlist_id = p_wishlist_id and m.member_user_id = v_uid) then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    return jsonb_build_object(
        'wishlist_id', p_wishlist_id,
        'name', v_name,
        'owner_display_name', (select p.display_name from public.profiles p where p.id = v_owner_id),
        -- Who the list is for, by name only: never the person's id or anything else of theirs.
        'person_name', (select pe.name from public.people pe
                        where pe.id = v_person and pe.owner_user_id = v_owner_id and pe.deleted_at is null),
        'wishes', coalesce((
            select jsonb_agg(jsonb_build_object(
                       'wish_id', w.id,
                       'name', w.name,
                       'note', w.note,
                       'estimated_price_amount_minor', w.estimated_price_amount_minor,
                       'estimated_price_currency_code', w.estimated_price_currency_code,
                       'product_url', w.product_url,
                       -- A Storage path the member can read through wish_photos_select_shared.
                       'photo_path', w.photo_path,
                       'reservation_state', case
                           when h.holder is null then 'AVAILABLE'
                           when h.holder = v_uid then 'RESERVED_BY_ME'
                           else 'RESERVED_BY_OTHER'
                       end)
                   order by w.created_at, w.id)
            from public.wishes w
            cross join lateral (select private.active_reservation_holder(w.id) as holder) h
            where w.wishlist_id = p_wishlist_id and w.deleted_at is null
        ), '[]'::jsonb));
end;
$$;
