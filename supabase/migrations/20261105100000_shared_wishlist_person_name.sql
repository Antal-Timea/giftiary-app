-- Sharing, phase 2D: members of a list kept for a person see who it is for.
--
-- get_shared_wishlist adds one field, person_name: the person's name while they are not deleted,
-- otherwise null, and null for the owner's own lists. Nothing else of the person is returned, not
-- their id, and people stays owner-only. The body is 2B's with that field added; create or replace
-- keeps the function's owner and privileges (authenticated only).

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
