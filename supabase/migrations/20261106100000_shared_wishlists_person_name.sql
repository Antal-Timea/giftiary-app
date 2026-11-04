-- Sharing, phase 2E: the shared-list summary names who a list kept for a person is for.
--
-- get_shared_wishlists adds person_name to each entry under 2D's rule: the person's name while they
-- are not deleted, otherwise null, and null for the owner's own lists. Nothing else of the person is
-- returned, not their id. The body is 2B's with that field added; create or replace keeps the
-- function's owner and privileges (authenticated only).

create or replace function public.get_shared_wishlists()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    return coalesce((
        select jsonb_agg(jsonb_build_object(
                   'wishlist_id', l.id, 'name', l.name, 'owner_display_name', p.display_name,
                   'person_name', pe.name)
               order by l.name, l.id)
        from public.wishlist_members m
        join public.wishlists l on l.id = m.wishlist_id and l.deleted_at is null
        left join public.profiles p on p.id = l.owner_user_id
        -- Who the list is for, by name only, as in get_shared_wishlist.
        left join public.people pe
            on pe.id = l.person_id and pe.owner_user_id = l.owner_user_id and pe.deleted_at is null
        where m.member_user_id = v_uid
    ), '[]'::jsonb);
end;
$$;
