-- Phase 4A: what the sync protocol needs from the server, before any client syncs.
--
-- Every write to a synced row carries a client-generated mutation id. The server records each
-- accepted mutation in public.sync_mutations in the same transaction as the write, so a client
-- whose response was lost can prove that its mutation committed by finding the id there. The id is
-- the idempotency identity; version stays the optimistic-concurrency state (clients update with
-- "where version = expected"). Nothing here infers a commit from equal row content.
--
-- Applied migrations are immutable: private.set_sync_metadata() is replaced here, not edited there.

-- ---------------------------------------------------------------------------------------------
-- Mutation identity on every synced row
-- ---------------------------------------------------------------------------------------------

-- Nullable only for rows written before this migration; every new write must set it (see below).
alter table public.people add column last_mutation_id uuid;
alter table public.occasions add column last_mutation_id uuid;
alter table public.gift_ideas add column last_mutation_id uuid;
alter table public.gifts add column last_mutation_id uuid;

-- ---------------------------------------------------------------------------------------------
-- The mutation ledger
-- ---------------------------------------------------------------------------------------------

-- One row per accepted mutation, kept indefinitely. Its existence after commit is the proof that
-- the mutation committed; recorded_at is for diagnostics only. The primary key makes any reuse of
-- a mutation id, on the same row or another, fail with the write it belongs to.
create table public.sync_mutations (
    mutation_id       uuid primary key,
    -- Account deletion must be designed explicitly; like the synced rows, the ledger never
    -- silently disappears with its user.
    owner_user_id     uuid not null references auth.users (id),
    entity_type       text not null,
    entity_id         uuid not null,
    resulting_version bigint not null,
    recorded_at       timestamptz not null default now(),
    constraint sync_mutations_entity_type check (entity_type in ('people', 'occasions', 'gift_ideas', 'gifts'))
);

create index sync_mutations_owner_user_id_idx on public.sync_mutations (owner_user_id);

-- ---------------------------------------------------------------------------------------------
-- Version, timestamps and mutation identity
-- ---------------------------------------------------------------------------------------------

-- Replaces the version from 20261001101657. Unchanged: the server sets version and timestamps,
-- and id and owner never change. New: every insert and every update must carry a mutation id
-- that this row has not just been written with. A fresh id always makes the row different, so
-- every accepted update is exactly one new version; an update that "changes nothing" no longer
-- exists, because a write without a new mutation is rejected.
--
-- A retry of mutation M that already committed never reaches this trigger: its
-- "where version = expected" no longer matches (update), or its id already exists (insert).
create or replace function private.set_sync_metadata()
    returns trigger
    language plpgsql
    set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        if new.last_mutation_id is null then
            raise exception 'a new % row needs a mutation id', tg_table_name
                using errcode = 'not_null_violation';
        end if;
        new.version := 1;
        new.created_at := now();
        new.updated_at := now();
        return new;
    end if;

    if new.id is distinct from old.id then
        raise exception 'id of % % cannot be changed', tg_table_name, old.id
            using errcode = 'insufficient_privilege';
    end if;
    if new.owner_user_id is distinct from old.owner_user_id then
        raise exception 'owner_user_id of % % cannot be changed', tg_table_name, old.id
            using errcode = 'insufficient_privilege';
    end if;
    if new.last_mutation_id is null then
        raise exception 'a change to % % needs a mutation id', tg_table_name, old.id
            using errcode = 'not_null_violation';
    end if;
    if new.last_mutation_id = old.last_mutation_id then
        raise exception 'mutation % was already applied to % %', new.last_mutation_id, tg_table_name, old.id
            using errcode = 'unique_violation';
    end if;

    new.version := old.version + 1;
    new.created_at := old.created_at;
    new.updated_at := now();
    return new;
end;
$$;

-- Records the mutation in the same transaction as the row write, so the proof exists exactly
-- when the write committed. Runs as its owner because clients may not write the ledger at all:
-- a client-written ledger row would be a forged proof. Kept to this one insert, with an empty
-- search_path and fully qualified names; every value comes from the row being written.
create function private.record_sync_mutation()
    returns trigger
    language plpgsql
    security definer
    set search_path = ''
as $$
begin
    insert into public.sync_mutations (mutation_id, owner_user_id, entity_type, entity_id, resulting_version)
    values (new.last_mutation_id, new.owner_user_id, tg_table_name, new.id, new.version);
    return null;
end;
$$;

create trigger record_sync_mutation after insert or update on public.people
    for each row execute function private.record_sync_mutation();
create trigger record_sync_mutation after insert or update on public.occasions
    for each row execute function private.record_sync_mutation();
create trigger record_sync_mutation after insert or update on public.gift_ideas
    for each row execute function private.record_sync_mutation();
create trigger record_sync_mutation after insert or update on public.gifts
    for each row execute function private.record_sync_mutation();

-- Triggers run without EXECUTE; nobody may call these directly.
revoke execute on function private.set_sync_metadata() from public, anon, authenticated;
revoke execute on function private.record_sync_mutation() from public, anon, authenticated;

-- ---------------------------------------------------------------------------------------------
-- Ledger access: read your own entries, write nothing
-- ---------------------------------------------------------------------------------------------

revoke all on table public.sync_mutations from public, anon, authenticated, service_role;
grant select on table public.sync_mutations to authenticated;
-- Server-side administration only, as for the synced tables; its key never ships to clients.
grant select, insert, update, delete on table public.sync_mutations to service_role;

alter table public.sync_mutations enable row level security;

create policy "sync_mutations: owner can read"
    on public.sync_mutations for select to authenticated
    using (owner_user_id = (select auth.uid()));

-- ---------------------------------------------------------------------------------------------
-- Full owner-scoped pull: where owner_user_id = A and id > last order by id limit n
-- ---------------------------------------------------------------------------------------------

-- The composite index also serves every lookup the owner-only index did (RLS filters and the
-- foreign key to auth.users), so it replaces it.
drop index public.people_owner_user_id_idx;
drop index public.occasions_owner_user_id_idx;
drop index public.gift_ideas_owner_user_id_idx;
drop index public.gifts_owner_user_id_idx;

create index people_owner_user_id_id_idx on public.people (owner_user_id, id);
create index occasions_owner_user_id_id_idx on public.occasions (owner_user_id, id);
create index gift_ideas_owner_user_id_id_idx on public.gift_ideas (owner_user_id, id);
create index gifts_owner_user_id_id_idx on public.gifts (owner_user_id, id);
