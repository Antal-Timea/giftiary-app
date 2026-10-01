-- Who may do what. Two independent layers:
--   * grants decide which operations a role may attempt at all,
--   * row level security decides which rows those operations may touch.
-- Clients never hard-delete: deletion is an UPDATE of deleted_at, so DELETE is granted to no
-- client role and has no policy.
--
-- auth.uid() is wrapped in a sub-select so Postgres evaluates it once per statement.

-- ---------------------------------------------------------------------------------------------
-- Helper functions are internal
-- ---------------------------------------------------------------------------------------------

-- Triggers call these without needing EXECUTE; nobody should call them directly.
revoke all on schema private from public, anon, authenticated;
revoke execute on all functions in schema private from public, anon, authenticated;

-- ---------------------------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------------------------

-- Start from nothing, whatever the project's default privileges are, then grant what is needed.
revoke all on table public.profiles, public.people, public.occasions, public.gift_ideas, public.gifts
    from public, anon, authenticated, service_role;

-- A profile is created by the sign-up trigger; the user may only edit its descriptive fields.
grant select on table public.profiles to authenticated;
grant update (display_name, avatar_path, preferred_locale) on table public.profiles to authenticated;

grant select, insert, update on table public.people, public.occasions, public.gift_ideas, public.gifts
    to authenticated;

-- Server-side administration such as support fixes and the future account deletion.
-- service_role bypasses RLS but still needs table privileges, and with auto_expose_new_tables
-- off it gets none by default. Its key is never shipped to clients.
grant select, insert, update, delete
    on table public.profiles, public.people, public.occasions, public.gift_ideas, public.gifts
    to service_role;

-- ---------------------------------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.people enable row level security;
alter table public.occasions enable row level security;
alter table public.gift_ideas enable row level security;
alter table public.gifts enable row level security;

-- Profiles are private for now. Wishlist sharing will need a deliberately narrow lookup instead
-- of opening this table.
create policy "profiles: owner can read"
    on public.profiles for select to authenticated
    using (id = (select auth.uid()));

create policy "profiles: owner can update"
    on public.profiles for update to authenticated
    using (id = (select auth.uid()))
    with check (id = (select auth.uid()));

-- The same three policies for each synced table. INSERT and UPDATE both check the new row, so a
-- row cannot be created for, or handed over to, another user.

create policy "people: owner can read"
    on public.people for select to authenticated
    using (owner_user_id = (select auth.uid()));
create policy "people: owner can create"
    on public.people for insert to authenticated
    with check (owner_user_id = (select auth.uid()));
create policy "people: owner can update"
    on public.people for update to authenticated
    using (owner_user_id = (select auth.uid()))
    with check (owner_user_id = (select auth.uid()));

create policy "occasions: owner can read"
    on public.occasions for select to authenticated
    using (owner_user_id = (select auth.uid()));
create policy "occasions: owner can create"
    on public.occasions for insert to authenticated
    with check (owner_user_id = (select auth.uid()));
create policy "occasions: owner can update"
    on public.occasions for update to authenticated
    using (owner_user_id = (select auth.uid()))
    with check (owner_user_id = (select auth.uid()));

create policy "gift_ideas: owner can read"
    on public.gift_ideas for select to authenticated
    using (owner_user_id = (select auth.uid()));
create policy "gift_ideas: owner can create"
    on public.gift_ideas for insert to authenticated
    with check (owner_user_id = (select auth.uid()));
create policy "gift_ideas: owner can update"
    on public.gift_ideas for update to authenticated
    using (owner_user_id = (select auth.uid()))
    with check (owner_user_id = (select auth.uid()));

create policy "gifts: owner can read"
    on public.gifts for select to authenticated
    using (owner_user_id = (select auth.uid()));
create policy "gifts: owner can create"
    on public.gifts for insert to authenticated
    with check (owner_user_id = (select auth.uid()));
create policy "gifts: owner can update"
    on public.gifts for update to authenticated
    using (owner_user_id = (select auth.uid()))
    with check (owner_user_id = (select auth.uid()));
