-- Switches the app reads before anyone signs in, such as whether the sign-in screen offers
-- Facebook Login. Clients only read them, signed in or not; a value is changed by the project owner
-- in the dashboard (Table Editor or SQL editor), never through the API.
--
-- The app treats a missing row, an unreadable table or any failure as "off", so a flag can only
-- ever turn something on.

create table public.feature_flags (
    key text primary key check (key ~ '^[a-z][a-z0-9_]{0,62}$'),
    enabled boolean not null default false
);

comment on table public.feature_flags is
    'Read-only switches for the app, readable before sign-in. Change values in the dashboard only.';

alter table public.feature_flags enable row level security;

-- Supabase grants every new public table to the API roles by default; these may only read.
revoke all on table public.feature_flags from public, anon, authenticated;
grant select on table public.feature_flags to anon, authenticated;

create policy feature_flags_readable on public.feature_flags
    for select to anon, authenticated
    using (true);

-- Facebook Login stays hidden until the Meta app is live for every tester.
insert into public.feature_flags (key, enabled) values ('facebook_login_enabled', false);
