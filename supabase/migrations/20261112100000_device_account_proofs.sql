-- Lets a device learn that an account it holds data for was deleted from elsewhere, so it can remove
-- that data, without the server keeping anything about the account after its deletion and without a
-- way to ask about anyone else's account.
--
--   * While signed in, each device registers the SHA-256 of a random 32-byte secret only it knows.
--     The row belongs to the account and goes with it: ON DELETE CASCADE from auth.users, so
--     private.delete_account (and any other removal of the user) removes it, and no tombstone stays.
--   * Later, with no session of that account left, the device asks whether its secret still belongs
--     to a live account. The question names no account: the only input is the secret, and the only
--     answer is one boolean. Without a registered secret the answer is always false, so nobody can
--     learn anything about an account whose secret they do not hold.
--   * A registered row never changes and nothing but the account's removal deletes it: there is no
--     update or delete path. So for a device that registered its secret, "false" means the account
--     is gone. Keep it that way; a future cleanup of these rows would make devices purge live data.

create table private.account_device_proofs (
    proof_hash text primary key check (proof_hash ~ '^[0-9a-f]{64}$'),
    user_id uuid not null references auth.users (id) on delete cascade
);

create index account_device_proofs_user_id_idx on private.account_device_proofs (user_id);

revoke all on table private.account_device_proofs from public, anon, authenticated, service_role;

-- Each install registers once per account; reinstalls add rows. A device past the limit simply gets
-- no proof and keeps its data, which is the safe side.
create function private.account_device_proofs_max()
returns integer
language sql
immutable
set search_path = ''
as $$ select 20 $$;

revoke all on function private.account_device_proofs_max() from public, anon, authenticated, service_role;

-- ---------------------------------------------------------------------------------------------
-- Registration: as the signed-in account, for that account only
-- ---------------------------------------------------------------------------------------------

create function public.register_device_proof(p_proof_hash text)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_owner uuid;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    if p_proof_hash is null or p_proof_hash !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid proof' using errcode = '22023';
    end if;

    -- One registration of an account at a time, so the limit holds.
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('account_device_proofs:' || v_uid::text, 0));

    select p.user_id into v_owner from private.account_device_proofs p where p.proof_hash = p_proof_hash;
    if v_owner = v_uid then
        -- A retry after a lost answer.
        return;
    elsif v_owner is not null then
        raise exception 'invalid proof' using errcode = '22023';
    end if;

    if (select pg_catalog.count(*) from private.account_device_proofs p where p.user_id = v_uid)
        >= private.account_device_proofs_max() then
        raise exception 'too many devices' using errcode = '54000';
    end if;

    -- A token outliving its deleted account fails here on the foreign key (23503).
    insert into private.account_device_proofs (proof_hash, user_id) values (p_proof_hash, v_uid);
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- The question: does this secret still belong to a live account?
-- ---------------------------------------------------------------------------------------------

create function public.device_proof_is_live(p_proof text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    -- Malformed input is an error, never false: false is what lets a device delete its data.
    if p_proof is null or p_proof !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid proof' using errcode = '22023';
    end if;
    return exists (
        select 1
        from private.account_device_proofs p
        where p.proof_hash = pg_catalog.encode(pg_catalog.sha256(pg_catalog.decode(p_proof, 'hex')), 'hex')
    );
end;
$$;

revoke all on function public.register_device_proof(text) from public, anon, authenticated, service_role;
revoke all on function public.device_proof_is_live(text) from public, anon, authenticated, service_role;
grant execute on function public.register_device_proof(text) to authenticated;
-- Asked with no session at all (the account's session is gone), or under another signed-in account.
grant execute on function public.device_proof_is_live(text) to anon, authenticated;
