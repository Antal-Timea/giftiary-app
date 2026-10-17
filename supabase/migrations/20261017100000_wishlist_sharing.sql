-- Wishlists, phase W3: sharing an own wishlist with signed-in members through hashed, short-lived
-- invites, and reservations of its wishes by those members.
--
-- Everything here is reachable only through the security definer functions below. The three
-- tables have row level security with no policies and no client grants, are not published to
-- realtime, and never enter the owner's sync or mutation ledger.
--
-- The owner must never learn whether a wish is reserved, by whom, or how many are: not from rows,
-- versions or the ledger, not from errors, and not from waiting. So:
--   * wish_reservations has no foreign key at all. A key to wishes, wishlists or memberships
--     would make a reservation lock rows the owner can write, and the owner's write would then
--     wait for it;
--   * no reservation function locks a wish, wishlist or membership row; it reads them plainly;
--   * whether a reservation counts is decided at every read (private.active_reservation_holder):
--     removing a member or a member leaving releases their reservations logically, without
--     touching them, and a stale row is taken over by the next member who reserves the wish;
--   * guest functions reject the owner before they look at reservations at all.
--
-- Invite and member limits are serialized per wishlist with a transaction-scoped advisory lock,
-- which only the invite functions take. Joining is visible to the owner anyway.

-- ---------------------------------------------------------------------------------------------
-- Crypto: tokens come from pgcrypto's random bytes; only their SHA-256 (core) is stored
-- ---------------------------------------------------------------------------------------------

create extension if not exists pgcrypto with schema extensions;

-- The functions below call extensions.gen_random_bytes by its qualified name; refuse to continue
-- on a database where pgcrypto lives anywhere else, instead of failing at the first invite.
do $$
begin
    if (select extnamespace::regnamespace::text from pg_catalog.pg_extension where extname = 'pgcrypto')
        is distinct from 'extensions' then
        raise exception 'pgcrypto must be installed in schema extensions';
    end if;
end
$$;

-- ---------------------------------------------------------------------------------------------
-- Invites: only the hash of the token is kept
-- ---------------------------------------------------------------------------------------------

create table public.wishlist_invites (
    id            uuid primary key default gen_random_uuid(),
    wishlist_id   uuid not null,
    owner_user_id uuid not null references auth.users (id),
    -- SHA-256 of the 32 random token bytes. There is deliberately no column for the token itself.
    token_hash    bytea not null,
    created_at    timestamptz not null default now(),
    expires_at    timestamptz not null,
    revoked_at    timestamptz,
    constraint wishlist_invites_wishlist_same_owner_fk
        foreign key (wishlist_id, owner_user_id) references public.wishlists (id, owner_user_id),
    constraint wishlist_invites_token_hash_key unique (token_hash),
    constraint wishlist_invites_token_hash_length check (octet_length(token_hash) = 32),
    constraint wishlist_invites_expiry
        check (expires_at > created_at and expires_at <= created_at + interval '14 days')
);

create index wishlist_invites_wishlist_id_idx on public.wishlist_invites (wishlist_id);

-- ---------------------------------------------------------------------------------------------
-- Members: membership_id names one uninterrupted membership, so a member who leaves and joins
-- again never gets their old reservations back
-- ---------------------------------------------------------------------------------------------

create table public.wishlist_members (
    wishlist_id    uuid not null,
    owner_user_id  uuid not null,
    member_user_id uuid not null references auth.users (id),
    membership_id  uuid not null default gen_random_uuid(),
    joined_at      timestamptz not null default now(),
    constraint wishlist_members_pkey primary key (wishlist_id, member_user_id),
    constraint wishlist_members_membership_id_key unique (membership_id),
    constraint wishlist_members_wishlist_same_owner_fk
        foreign key (wishlist_id, owner_user_id) references public.wishlists (id, owner_user_id),
    constraint wishlist_members_not_owner check (member_user_id <> owner_user_id)
);

create index wishlist_members_member_user_id_idx on public.wishlist_members (member_user_id);

-- ---------------------------------------------------------------------------------------------
-- Reservations: no foreign keys of any kind (see the top of this file)
-- ---------------------------------------------------------------------------------------------

create table public.wish_reservations (
    wish_id          uuid primary key,
    -- The wish's list and the reserver's membership when the reservation was made, always taken
    -- from the current rows by the reservation functions, never from the caller.
    wishlist_id      uuid not null,
    reserver_user_id uuid not null,
    membership_id    uuid not null,
    reserved_at      timestamptz not null default now()
);

create index wish_reservations_reserver_user_id_idx on public.wish_reservations (reserver_user_id);
create index wish_reservations_wishlist_id_idx on public.wish_reservations (wishlist_id);

-- ---------------------------------------------------------------------------------------------
-- Clients reach these tables only through the functions below
-- ---------------------------------------------------------------------------------------------

revoke all on table public.wishlist_invites, public.wishlist_members, public.wish_reservations
    from public, anon, authenticated, service_role;
-- Server-side administration only; its key never ships to clients.
grant select, insert, update, delete
    on table public.wishlist_invites, public.wishlist_members, public.wish_reservations
    to service_role;

-- Row level security without policies: nobody but the table owner (the functions) reads a row.
alter table public.wishlist_invites enable row level security;
alter table public.wishlist_members enable row level security;
alter table public.wish_reservations enable row level security;

-- ---------------------------------------------------------------------------------------------
-- Internal helpers
-- ---------------------------------------------------------------------------------------------

-- The second key of a wishlist's capacity lock: the first 32 bits of its id. Two lists sharing a
-- key only wait for each other's invite calls; each still counts its own rows.
create function private.wishlist_capacity_key(p_wishlist_id uuid)
returns integer
language sql
immutable
set search_path = ''
as $$
    select ('x' || substr(replace(p_wishlist_id::text, '-', ''), 1, 8))::bit(32)::integer
$$;

-- The member holding the active reservation of a wish, or null if nobody does. A reservation
-- counts only while its wish is active on an active, shareable list it was made on, and its
-- reserver is still a member through the same membership. Plain reads only: nothing is locked.
create function private.active_reservation_holder(p_wish_id uuid)
returns uuid
language sql
stable
set search_path = ''
as $$
    select r.reserver_user_id
    from public.wish_reservations r
    join public.wishes w
        on w.id = r.wish_id and w.deleted_at is null and w.wishlist_id = r.wishlist_id
    join public.wishlists l
        on l.id = w.wishlist_id and l.deleted_at is null and l.person_id is null
    join public.wishlist_members m
        on m.wishlist_id = r.wishlist_id
       and m.member_user_id = r.reserver_user_id
       and m.membership_id = r.membership_id
    where r.wish_id = p_wish_id
$$;

revoke all on function private.wishlist_capacity_key(uuid) from public, anon, authenticated, service_role;
revoke all on function private.active_reservation_holder(uuid) from public, anon, authenticated, service_role;

-- ---------------------------------------------------------------------------------------------
-- Owner functions
-- ---------------------------------------------------------------------------------------------

-- Returns the token once; only its hash is stored.
create function public.create_wishlist_invite(p_wishlist_id uuid, p_valid_days integer default 7)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_uid    uuid := auth.uid();
    v_raw    bytea;
    v_invite public.wishlist_invites;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    if p_valid_days is null or p_valid_days not between 1 and 14 then
        raise exception 'valid days must be between 1 and 14' using errcode = '22023';
    end if;
    if not exists (
        select 1 from public.wishlists l
        where l.id = p_wishlist_id and l.owner_user_id = v_uid and l.deleted_at is null and l.person_id is null
    ) then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    -- Count and insert are serialized per list. The count is a new statement, so it sees every
    -- invite the previous holder of the lock committed.
    perform pg_catalog.pg_advisory_xact_lock(1734960756, private.wishlist_capacity_key(p_wishlist_id));
    if (select count(*) from public.wishlist_invites i
        where i.wishlist_id = p_wishlist_id and i.revoked_at is null and i.expires_at > now()) >= 5 then
        raise exception 'invite limit reached' using errcode = 'P0001';
    end if;

    v_raw := extensions.gen_random_bytes(32);
    insert into public.wishlist_invites (wishlist_id, owner_user_id, token_hash, expires_at)
    values (p_wishlist_id, v_uid, pg_catalog.sha256(v_raw), now() + p_valid_days * interval '1 day')
    returning * into v_invite;

    return jsonb_build_object(
        'invite_id', v_invite.id,
        'token', rtrim(translate(encode(v_raw, 'base64'), '+/', '-_'), '='),
        'expires_at', v_invite.expires_at);
end;
$$;

create function public.revoke_wishlist_invite(p_invite_id uuid)
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
    if not exists (select 1 from public.wishlist_invites i where i.id = p_invite_id and i.owner_user_id = v_uid) then
        raise exception 'not available' using errcode = 'P0002';
    end if;
    update public.wishlist_invites set revoked_at = now()
    where id = p_invite_id and owner_user_id = v_uid and revoked_at is null;
end;
$$;

-- Active invites and members of the caller's list. Never anything about reservations.
create function public.list_wishlist_access(p_wishlist_id uuid)
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
    if not exists (
        select 1 from public.wishlists l
        where l.id = p_wishlist_id and l.owner_user_id = v_uid and l.deleted_at is null
    ) then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    return jsonb_build_object(
        'invites', coalesce((
            select jsonb_agg(jsonb_build_object(
                       'invite_id', i.id, 'created_at', i.created_at, 'expires_at', i.expires_at)
                   order by i.created_at, i.id)
            from public.wishlist_invites i
            where i.wishlist_id = p_wishlist_id and i.revoked_at is null and i.expires_at > now()
        ), '[]'::jsonb),
        'members', coalesce((
            select jsonb_agg(jsonb_build_object(
                       'member_user_id', m.member_user_id, 'display_name', p.display_name, 'joined_at', m.joined_at)
                   order by m.joined_at, m.member_user_id)
            from public.wishlist_members m
            left join public.profiles p on p.id = m.member_user_id
            where m.wishlist_id = p_wishlist_id
        ), '[]'::jsonb));
end;
$$;

-- Ends the membership only. The member's reservations stop counting at once because their
-- membership is gone; they are neither read nor touched here, so this never waits on them.
create function public.remove_wishlist_member(p_wishlist_id uuid, p_member_user_id uuid)
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
    if not exists (select 1 from public.wishlists l where l.id = p_wishlist_id and l.owner_user_id = v_uid) then
        raise exception 'not available' using errcode = 'P0002';
    end if;
    delete from public.wishlist_members
    where wishlist_id = p_wishlist_id and member_user_id = p_member_user_id and owner_user_id = v_uid;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- Member functions
-- ---------------------------------------------------------------------------------------------

-- Joins the list of a valid invite. Every failure looks the same, so a token, a list's size or
-- its owner cannot be probed. Joining again keeps the existing membership and its id.
create function public.redeem_wishlist_invite(p_token text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_uid         uuid := auth.uid();
    v_raw         bytea;
    v_invite_id   uuid;
    v_wishlist_id uuid;
    v_owner_id    uuid;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    if p_token is null or p_token !~ '^[A-Za-z0-9_-]{43}$' then
        raise exception 'not available' using errcode = 'P0002';
    end if;
    begin
        v_raw := decode(translate(p_token, '-_', '+/') || '=', 'base64');
    exception when others then
        raise exception 'not available' using errcode = 'P0002';
    end;
    if octet_length(v_raw) <> 32 then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    select i.id, i.wishlist_id, l.owner_user_id into v_invite_id, v_wishlist_id, v_owner_id
    from public.wishlist_invites i
    join public.wishlists l on l.id = i.wishlist_id and l.owner_user_id = i.owner_user_id
    where i.token_hash = pg_catalog.sha256(v_raw)
      and i.revoked_at is null and i.expires_at > now()
      and l.deleted_at is null and l.person_id is null;
    if not found or v_owner_id = v_uid then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(1734960756, private.wishlist_capacity_key(v_wishlist_id));

    -- A revocation committed while this call waited for the lock wins.
    if not exists (
        select 1 from public.wishlist_invites i
        join public.wishlists l on l.id = i.wishlist_id
        where i.id = v_invite_id and i.revoked_at is null and i.expires_at > now()
          and l.deleted_at is null and l.person_id is null
    ) then
        raise exception 'not available' using errcode = 'P0002';
    end if;
    if exists (select 1 from public.wishlist_members m where m.wishlist_id = v_wishlist_id and m.member_user_id = v_uid) then
        return jsonb_build_object('wishlist_id', v_wishlist_id);
    end if;
    if (select count(*) from public.wishlist_members m where m.wishlist_id = v_wishlist_id) >= 50 then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id)
    values (v_wishlist_id, v_owner_id, v_uid)
    on conflict (wishlist_id, member_user_id) do nothing;
    return jsonb_build_object('wishlist_id', v_wishlist_id);
end;
$$;

-- Ends the caller's own membership only; like a removal, it releases reservations logically.
create function public.leave_wishlist(p_wishlist_id uuid)
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
    delete from public.wishlist_members where wishlist_id = p_wishlist_id and member_user_id = v_uid;
end;
$$;

create function public.get_shared_wishlists()
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
                   'wishlist_id', l.id, 'name', l.name, 'owner_display_name', p.display_name)
               order by l.name, l.id)
        from public.wishlist_members m
        join public.wishlists l on l.id = m.wishlist_id and l.deleted_at is null and l.person_id is null
        left join public.profiles p on p.id = l.owner_user_id
        where m.member_user_id = v_uid
    ), '[]'::jsonb);
end;
$$;

-- A shared list as one of its members sees it: display fields only, and for each wish whether it
-- is free, reserved by the caller or by someone else, never by whom.
create function public.get_shared_wishlist(p_wishlist_id uuid)
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
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    select l.owner_user_id, l.name into v_owner_id, v_name
    from public.wishlists l
    where l.id = p_wishlist_id and l.deleted_at is null and l.person_id is null;
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

-- Reserves a wish for the caller, or reports who holds it. Only the reservation row is ever
-- locked: competing members wait for each other there, the owner never waits for anything.
create function public.reserve_wish(p_wish_id uuid)
returns text
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_uid           uuid := auth.uid();
    v_wishlist_id   uuid;
    v_owner_id      uuid;
    v_membership_id uuid;
    v_holder        uuid;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    -- The wish's list comes from the wish itself, never from the caller.
    select w.wishlist_id, l.owner_user_id into v_wishlist_id, v_owner_id
    from public.wishes w
    join public.wishlists l on l.id = w.wishlist_id
    where w.id = p_wish_id and w.deleted_at is null and l.deleted_at is null and l.person_id is null;
    -- The owner is turned away here, before reservations are looked at.
    if not found or v_owner_id = v_uid then
        raise exception 'not available' using errcode = 'P0002';
    end if;
    select m.membership_id into v_membership_id
    from public.wishlist_members m
    where m.wishlist_id = v_wishlist_id and m.member_user_id = v_uid;
    if not found then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    for attempt in 1..3 loop
        insert into public.wish_reservations (wish_id, wishlist_id, reserver_user_id, membership_id)
        values (p_wish_id, v_wishlist_id, v_uid, v_membership_id)
        on conflict (wish_id) do nothing;
        if found then
            return 'RESERVED_BY_ME';
        end if;

        perform 1 from public.wish_reservations r where r.wish_id = p_wish_id for update;
        if not found then
            continue;  -- released meanwhile: try the insert again
        end if;
        v_holder := private.active_reservation_holder(p_wish_id);
        if v_holder is not null then
            return case when v_holder = v_uid then 'RESERVED_BY_ME' else 'RESERVED_BY_OTHER' end;
        end if;

        -- A stale row (moved wish, ended membership, ...) is taken over under its lock.
        update public.wish_reservations
        set wishlist_id = v_wishlist_id, reserver_user_id = v_uid, membership_id = v_membership_id, reserved_at = now()
        where wish_id = p_wish_id;
        return 'RESERVED_BY_ME';
    end loop;
    raise exception 'try again' using errcode = '40001';
end;
$$;

-- Releases the caller's own active reservation; anything else is left as it is.
create function public.release_wish(p_wish_id uuid)
returns text
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_uid           uuid := auth.uid();
    v_wishlist_id   uuid;
    v_owner_id      uuid;
    v_membership_id uuid;
    v_holder        uuid;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    select w.wishlist_id, l.owner_user_id into v_wishlist_id, v_owner_id
    from public.wishes w
    join public.wishlists l on l.id = w.wishlist_id
    where w.id = p_wish_id and w.deleted_at is null and l.deleted_at is null and l.person_id is null;
    if not found or v_owner_id = v_uid then
        raise exception 'not available' using errcode = 'P0002';
    end if;
    select m.membership_id into v_membership_id
    from public.wishlist_members m
    where m.wishlist_id = v_wishlist_id and m.member_user_id = v_uid;
    if not found then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    delete from public.wish_reservations
    where wish_id = p_wish_id and reserver_user_id = v_uid
      and membership_id = v_membership_id and wishlist_id = v_wishlist_id;

    v_holder := private.active_reservation_holder(p_wish_id);
    return case
        when v_holder is null then 'AVAILABLE'
        when v_holder = v_uid then 'RESERVED_BY_ME'
        else 'RESERVED_BY_OTHER'
    end;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- Function privileges: signed-in users only. Supabase's default privileges would also let anon
-- execute new public functions, so every grant starts from nothing.
-- ---------------------------------------------------------------------------------------------

revoke all on function public.create_wishlist_invite(uuid, integer) from public, anon, authenticated, service_role;
revoke all on function public.revoke_wishlist_invite(uuid) from public, anon, authenticated, service_role;
revoke all on function public.list_wishlist_access(uuid) from public, anon, authenticated, service_role;
revoke all on function public.remove_wishlist_member(uuid, uuid) from public, anon, authenticated, service_role;
revoke all on function public.redeem_wishlist_invite(text) from public, anon, authenticated, service_role;
revoke all on function public.leave_wishlist(uuid) from public, anon, authenticated, service_role;
revoke all on function public.get_shared_wishlists() from public, anon, authenticated, service_role;
revoke all on function public.get_shared_wishlist(uuid) from public, anon, authenticated, service_role;
revoke all on function public.reserve_wish(uuid) from public, anon, authenticated, service_role;
revoke all on function public.release_wish(uuid) from public, anon, authenticated, service_role;

grant execute on function public.create_wishlist_invite(uuid, integer) to authenticated;
grant execute on function public.revoke_wishlist_invite(uuid) to authenticated;
grant execute on function public.list_wishlist_access(uuid) to authenticated;
grant execute on function public.remove_wishlist_member(uuid, uuid) to authenticated;
grant execute on function public.redeem_wishlist_invite(text) to authenticated;
grant execute on function public.leave_wishlist(uuid) to authenticated;
grant execute on function public.get_shared_wishlists() to authenticated;
grant execute on function public.get_shared_wishlist(uuid) to authenticated;
grant execute on function public.reserve_wish(uuid) to authenticated;
grant execute on function public.release_wish(uuid) to authenticated;
