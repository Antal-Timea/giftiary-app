-- Account deletion: a signed-in user deletes their account and everything it owns, in one
-- transaction.
--
-- This is not the synced deletion of a row. Synced deletes leave tombstones so the account's other
-- devices learn of them; once the account is gone it has no devices that can sync (its sessions and
-- refresh tokens go with auth.users), so nothing is kept: every row is deleted outright.
--
--   * Only the caller is ever deleted: the public entry point takes no user id and reads auth.uid().
--   * The account's footprint on other users' lists goes too: its memberships and its reservations,
--     and every membership and reservation on its own lists (reservations have no foreign keys, so
--     they are deleted explicitly).
--   * The foreign keys to auth.users keep their missing ON DELETE on purpose: after the account is
--     gone, a request still carrying its token cannot recreate any row (23503).
--   * Everything happens in the caller's transaction: any error leaves every row in place.

-- ---------------------------------------------------------------------------------------------
-- people.linked_user_id is not a feature yet: the app always sends null. Left writable, any client
-- could point its own person at another user's id, which reveals whether that id is an account
-- (23503) and, through ON DELETE SET NULL, makes that user's deletion fail: the cascading update
-- keeps the row's mutation id, which set_sync_metadata refuses. Existing values (none are expected)
-- are cleared as ordinary versioned changes first, so the owners' devices pull them.
-- ---------------------------------------------------------------------------------------------

update public.people
set linked_user_id = null, last_mutation_id = pg_catalog.gen_random_uuid()
where linked_user_id is not null;

alter table public.people
    add constraint people_linked_user_id_unused check (linked_user_id is null);

-- ---------------------------------------------------------------------------------------------
-- The deletion itself, for one account; reachable only through public.delete_my_account()
-- ---------------------------------------------------------------------------------------------

create function private.delete_account(p_user_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
    -- Two deletions of the same account run one after the other; the second finds nothing left.
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_user_id::text, 0));

    -- Sharing first: reservations and memberships by the account, and on its lists.
    delete from public.wish_reservations
    where reserver_user_id = p_user_id
       or wishlist_id in (select l.id from public.wishlists l where l.owner_user_id = p_user_id);
    delete from public.wishlist_members where member_user_id = p_user_id or owner_user_id = p_user_id;
    delete from public.wishlist_invites where owner_user_id = p_user_id;

    -- Owned rows, children before the rows their same-owner foreign keys point to.
    delete from public.wishes where owner_user_id = p_user_id;
    delete from public.wishlists where owner_user_id = p_user_id;
    delete from public.gifts where owner_user_id = p_user_id;
    delete from public.gift_ideas where owner_user_id = p_user_id;
    delete from public.occasions where owner_user_id = p_user_id;
    delete from public.people where owner_user_id = p_user_id;
    delete from public.sync_mutations where owner_user_id = p_user_id;

    -- Other accounts' people linked to this one, as versioned changes their devices pull. The
    -- check above keeps new links out; this stays so the deletion never depends on that check.
    update public.people
    set linked_user_id = null, last_mutation_id = pg_catalog.gen_random_uuid()
    where linked_user_id = p_user_id;

    -- Last: the account itself. Its profile, identities, sessions and refresh tokens cascade.
    delete from auth.users where id = p_user_id;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- The client entry point: deletes the caller, never anyone else
-- ---------------------------------------------------------------------------------------------

create function public.delete_my_account()
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    perform private.delete_account(v_uid);
end;
$$;

revoke all on function private.delete_account(uuid) from public, anon, authenticated, service_role;
revoke all on function public.delete_my_account() from public, anon, authenticated, service_role;
grant execute on function public.delete_my_account() to authenticated;
