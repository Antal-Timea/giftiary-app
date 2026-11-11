-- A wishlist's kind is fixed when it is created: the owner's own list (person_id null) or a list kept
-- for one of the owner's people (person_id set). Afterwards person_id cannot change.
--
-- Whether the owner may learn which wishes are reserved depends on that kind
-- (public.get_owner_reserved_wishes answers only for a list kept for a person). Changing person_id,
-- even for a moment, would let the owner of their own list read its reservations and then switch it back
-- without any guest noticing. So the rule is enforced here, for every client, and not left to the app,
-- which never changes it anyway.
--
-- Every change is refused, including from one person to another: the app has no such edit, and keeping
-- the column fixed is simpler than deciding which transitions are harmless. An update that rewrites
-- person_id with its current value, as a sync push of the whole row does, is not a change and passes.

create function private.keep_wishlist_person()
    returns trigger
    language plpgsql
    set search_path = ''
as $$
begin
    raise exception 'person_id of wishlists % cannot be changed', old.id
        using errcode = 'insufficient_privilege';
end;
$$;

revoke all on function private.keep_wishlist_person() from public, anon, authenticated, service_role;

create trigger keep_wishlist_person
    before update on public.wishlists
    for each row
    when (new.person_id is distinct from old.person_id)
    execute function private.keep_wishlist_person();
