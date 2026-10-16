-- Wishlists, phase W1: the user's own wishlists and wishes as private, owner-only synced rows,
-- exactly like people, occasions, gift ideas and gifts.
--
-- Sharing, invitations and reservations are not part of this phase. When they come, they live in
-- separate tables reachable only through functions: nothing here ever carries reservation state,
-- and a reservation will never write to a wish, so the owner can never learn of one from these
-- rows, their versions or the mutation ledger.
--
-- Applied migrations are immutable: the ledger's entity type check is replaced here, widened, not
-- edited where it was created.

-- ---------------------------------------------------------------------------------------------
-- Wishlists
-- ---------------------------------------------------------------------------------------------

create table public.wishlists (
    id               uuid primary key,
    owner_user_id    uuid not null references auth.users (id),
    -- A list kept for someone else; null for the user's own list. Only the user's own lists will
    -- ever be shareable, so a person's details never reach anyone the list is shared with.
    person_id        uuid,
    name             text not null,
    version          bigint not null default 1,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    deleted_at       timestamptz,
    last_mutation_id uuid,
    constraint wishlists_person_same_owner_fk
        foreign key (person_id, owner_user_id) references public.people (id, owner_user_id),
    constraint wishlists_name_not_blank check (btrim(name) <> ''),
    constraint wishlists_version_positive check (version >= 1),
    -- Target of the wishes' same-owner foreign key.
    constraint wishlists_id_owner_key unique (id, owner_user_id)
);

create index wishlists_owner_user_id_id_idx on public.wishlists (owner_user_id, id);
create index wishlists_person_id_idx on public.wishlists (person_id);

-- ---------------------------------------------------------------------------------------------
-- Wishes
-- ---------------------------------------------------------------------------------------------

-- A wish carries its owner like every synced row, always its wishlist's owner: the composite
-- foreign key makes any other owner impossible, and owner-scoped RLS, ledger entries and pulls
-- work for wishes exactly as for the other tables.
create table public.wishes (
    id                            uuid primary key,
    owner_user_id                 uuid not null references auth.users (id),
    wishlist_id                   uuid not null,
    name                          text not null,
    note                          text,
    estimated_price_amount_minor  bigint,
    estimated_price_currency_code public.currency_code,
    product_url                   text,
    -- A stable Storage path, never a signed URL.
    photo_path                    text,
    version                       bigint not null default 1,
    created_at                    timestamptz not null default now(),
    updated_at                    timestamptz not null default now(),
    deleted_at                    timestamptz,
    last_mutation_id              uuid,
    constraint wishes_wishlist_same_owner_fk
        foreign key (wishlist_id, owner_user_id) references public.wishlists (id, owner_user_id),
    constraint wishes_name_not_blank check (btrim(name) <> ''),
    constraint wishes_estimated_price_pair
        check ((estimated_price_amount_minor is null) = (estimated_price_currency_code is null)),
    constraint wishes_estimated_price_not_negative
        check (estimated_price_amount_minor is null or estimated_price_amount_minor >= 0),
    constraint wishes_version_positive check (version >= 1)
);

create index wishes_owner_user_id_id_idx on public.wishes (owner_user_id, id);
create index wishes_wishlist_id_idx on public.wishes (wishlist_id);

-- ---------------------------------------------------------------------------------------------
-- Server-managed metadata and the mutation ledger, as for the other synced tables
-- ---------------------------------------------------------------------------------------------

create trigger set_sync_metadata before insert or update on public.wishlists
    for each row execute function private.set_sync_metadata();
create trigger set_sync_metadata before insert or update on public.wishes
    for each row execute function private.set_sync_metadata();

create trigger record_sync_mutation after insert or update on public.wishlists
    for each row execute function private.record_sync_mutation();
create trigger record_sync_mutation after insert or update on public.wishes
    for each row execute function private.record_sync_mutation();

-- Widened, never narrowed: every existing ledger entry stays valid.
alter table public.sync_mutations drop constraint sync_mutations_entity_type;
alter table public.sync_mutations add constraint sync_mutations_entity_type
    check (entity_type in ('people', 'occasions', 'gift_ideas', 'gifts', 'wishlists', 'wishes'));

-- ---------------------------------------------------------------------------------------------
-- Grants and row level security: the owner only, no client deletes
-- ---------------------------------------------------------------------------------------------

revoke all on table public.wishlists, public.wishes from public, anon, authenticated, service_role;
grant select, insert, update on table public.wishlists, public.wishes to authenticated;
-- Server-side administration only; its key never ships to clients.
grant select, insert, update, delete on table public.wishlists, public.wishes to service_role;

alter table public.wishlists enable row level security;
alter table public.wishes enable row level security;

create policy "wishlists: owner can read"
    on public.wishlists for select to authenticated
    using (owner_user_id = (select auth.uid()));
create policy "wishlists: owner can create"
    on public.wishlists for insert to authenticated
    with check (owner_user_id = (select auth.uid()));
create policy "wishlists: owner can update"
    on public.wishlists for update to authenticated
    using (owner_user_id = (select auth.uid()))
    with check (owner_user_id = (select auth.uid()));

create policy "wishes: owner can read"
    on public.wishes for select to authenticated
    using (owner_user_id = (select auth.uid()));
create policy "wishes: owner can create"
    on public.wishes for insert to authenticated
    with check (owner_user_id = (select auth.uid()));
create policy "wishes: owner can update"
    on public.wishes for update to authenticated
    using (owner_user_id = (select auth.uid()))
    with check (owner_user_id = (select auth.uid()));
