-- Sharing, phase 4B: inviting someone to a wishlist by their email address, next to the bearer
-- links of W3, which stay exactly as they are.
--
-- The owner types an address; nothing on the owner's side ever looks at accounts, so creating,
-- listing and revoking answer the same whether the address belongs to anyone. An invite reaches a
-- signed-in user only through auth.users: their email must be confirmed and equal the invite's,
-- read inside these functions at every call, never taken from the client or its token's claims.
--
--   * One pending invite per list and address; inviting again while it is pending returns it.
--   * 14 days, then expired. Expiry is derived from expires_at; only a re-invite writes it down,
--     to free the address for a new pending invite.
--   * At most 20 pending invites per list (serialized with W3's per-list capacity lock) and 30
--     created per owner in any 24 hours (serialized with a per-owner lock).
--   * The owner sees PENDING or NOT_ACCEPTED (declined and expired alike), never which. Accepted
--     invites leave the owner's list: the recipient is a member then. Revoked ones are gone.
--   * Accepting joins the list like redeeming a link: under the capacity lock, within 50 members,
--     keeping an existing membership (and with it its reservations).
--   * No recipient user id is stored, so a recipient's account deletion changes nothing the owner
--     can see.

create table public.wishlist_recipient_invites (
    id              uuid primary key default gen_random_uuid(),
    wishlist_id     uuid not null,
    owner_user_id   uuid not null references auth.users (id),
    -- Trimmed and lowercased by create_recipient_invite; only the owner ever reads it back.
    recipient_email text not null,
    status          text not null default 'pending',
    created_at      timestamptz not null default now(),
    expires_at      timestamptz not null,
    closed_at       timestamptz,
    constraint wishlist_recipient_invites_wishlist_same_owner_fk
        foreign key (wishlist_id, owner_user_id) references public.wishlists (id, owner_user_id),
    constraint wishlist_recipient_invites_email_normalized
        check (recipient_email = lower(btrim(recipient_email)) and length(recipient_email) between 3 and 254),
    constraint wishlist_recipient_invites_status
        check (status in ('pending', 'accepted', 'declined', 'revoked', 'expired')),
    constraint wishlist_recipient_invites_closed check ((status = 'pending') = (closed_at is null)),
    constraint wishlist_recipient_invites_expiry
        check (expires_at > created_at and expires_at <= created_at + interval '14 days')
);

create index wishlist_recipient_invites_wishlist_id_idx on public.wishlist_recipient_invites (wishlist_id);
create unique index wishlist_recipient_invites_one_pending_idx
    on public.wishlist_recipient_invites (wishlist_id, recipient_email) where status = 'pending';
create index wishlist_recipient_invites_recipient_idx
    on public.wishlist_recipient_invites (recipient_email) where status = 'pending';
create index wishlist_recipient_invites_owner_created_idx
    on public.wishlist_recipient_invites (owner_user_id, created_at);

revoke all on table public.wishlist_recipient_invites from public, anon, authenticated, service_role;
grant select, insert, update, delete on table public.wishlist_recipient_invites to service_role;
alter table public.wishlist_recipient_invites enable row level security;

-- ---------------------------------------------------------------------------------------------
-- The caller's address, if it can receive invites: confirmed, trimmed and lowercased, else null
-- ---------------------------------------------------------------------------------------------

create function private.confirmed_email(p_user_id uuid)
returns text
language sql
stable
set search_path = ''
as $$
    select nullif(lower(btrim(u.email)), '')
    from auth.users u
    where u.id = p_user_id and u.email_confirmed_at is not null
$$;

revoke all on function private.confirmed_email(uuid) from public, anon, authenticated, service_role;

-- ---------------------------------------------------------------------------------------------
-- Owner functions
-- ---------------------------------------------------------------------------------------------

create function public.create_recipient_invite(p_wishlist_id uuid, p_email text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_uid    uuid := auth.uid();
    v_email  text := lower(btrim(p_email));
    v_invite public.wishlist_recipient_invites;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    if v_email is null or length(v_email) > 254 or v_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' then
        raise exception 'invalid email' using errcode = '22023';
    end if;
    -- Only the caller's own address is compared, confirmed or not; nobody else's is looked at.
    if v_email = (select lower(btrim(u.email)) from auth.users u where u.id = v_uid) then
        raise exception 'own email' using errcode = '22023';
    end if;
    if not exists (
        select 1 from public.wishlists l
        where l.id = p_wishlist_id and l.owner_user_id = v_uid and l.deleted_at is null
    ) then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    -- Per owner, then per list (the lock link redemption and acceptance take too).
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('recipient-invites:' || v_uid::text, 0));
    perform pg_catalog.pg_advisory_xact_lock(1734960756, private.wishlist_capacity_key(p_wishlist_id));

    select * into v_invite
    from public.wishlist_recipient_invites i
    where i.wishlist_id = p_wishlist_id and i.recipient_email = v_email and i.status = 'pending';
    if found and v_invite.expires_at > now() then
        return jsonb_build_object(
            'invite_id', v_invite.id, 'recipient_email', v_invite.recipient_email, 'state', 'PENDING',
            'created_at', v_invite.created_at, 'expires_at', v_invite.expires_at);
    elsif found then
        update public.wishlist_recipient_invites set status = 'expired', closed_at = v_invite.expires_at
        where id = v_invite.id;
    end if;

    if (select count(*) from public.wishlist_recipient_invites i
        where i.wishlist_id = p_wishlist_id and i.status = 'pending' and i.expires_at > now()) >= 20 then
        raise exception 'invite limit reached' using errcode = 'P0001';
    end if;
    if (select count(*) from public.wishlist_recipient_invites i
        where i.owner_user_id = v_uid and i.created_at > now() - interval '1 day') >= 30 then
        raise exception 'daily invite limit reached' using errcode = 'P0001';
    end if;

    insert into public.wishlist_recipient_invites (wishlist_id, owner_user_id, recipient_email, expires_at)
    values (p_wishlist_id, v_uid, v_email, now() + interval '14 days')
    returning * into v_invite;
    return jsonb_build_object(
        'invite_id', v_invite.id, 'recipient_email', v_invite.recipient_email, 'state', 'PENDING',
        'created_at', v_invite.created_at, 'expires_at', v_invite.expires_at);
end;
$$;

-- Pending and not accepted invites of the caller's list, oldest first.
create function public.list_recipient_invites(p_wishlist_id uuid)
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

    return coalesce((
        select jsonb_agg(jsonb_build_object(
                   'invite_id', i.id,
                   'recipient_email', i.recipient_email,
                   'state', case when i.status = 'pending' and i.expires_at > now() then 'PENDING' else 'NOT_ACCEPTED' end,
                   'created_at', i.created_at,
                   'expires_at', i.expires_at)
               order by i.created_at, i.id)
        from public.wishlist_recipient_invites i
        where i.wishlist_id = p_wishlist_id and i.status in ('pending', 'declined', 'expired')
    ), '[]'::jsonb);
end;
$$;

-- Withdraws a pending invite, or clears a not accepted one from the owner's list. An accepted
-- invite is left alone: the recipient is a member, removed with remove_wishlist_member.
create function public.revoke_recipient_invite(p_invite_id uuid)
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
    if not exists (select 1 from public.wishlist_recipient_invites i where i.id = p_invite_id and i.owner_user_id = v_uid) then
        raise exception 'not available' using errcode = 'P0002';
    end if;
    update public.wishlist_recipient_invites set status = 'revoked', closed_at = now()
    where id = p_invite_id and owner_user_id = v_uid and status in ('pending', 'declined', 'expired');
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- Recipient functions: the caller's confirmed address decides, at every call
-- ---------------------------------------------------------------------------------------------

-- Open invites to the caller's confirmed address, newest first. Nothing of the list beyond its
-- name and whom it is for (2D's rule), and nothing of its wishes, before accepting.
create function public.get_my_invites()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid   uuid := auth.uid();
    v_email text;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    v_email := private.confirmed_email(v_uid);
    if v_email is null then
        return '[]'::jsonb;
    end if;

    return coalesce((
        select jsonb_agg(jsonb_build_object(
                   'invite_id', i.id,
                   'wishlist_name', l.name,
                   'owner_display_name', p.display_name,
                   'person_name', pe.name,
                   'created_at', i.created_at,
                   'expires_at', i.expires_at)
               order by i.created_at desc, i.id)
        from public.wishlist_recipient_invites i
        join public.wishlists l on l.id = i.wishlist_id and l.owner_user_id = i.owner_user_id and l.deleted_at is null
        left join public.profiles p on p.id = l.owner_user_id
        left join public.people pe
            on pe.id = l.person_id and pe.owner_user_id = l.owner_user_id and pe.deleted_at is null
        where i.recipient_email = v_email and i.status = 'pending' and i.expires_at > now()
          and i.owner_user_id <> v_uid
    ), '[]'::jsonb);
end;
$$;

-- Accepts (joins the list, returning {wishlist_id}) or declines (returning {}) an open invite to
-- the caller's confirmed address. Every refusal looks the same.
create function public.respond_to_invite(p_invite_id uuid, p_accept boolean)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_uid         uuid := auth.uid();
    v_email       text;
    v_wishlist_id uuid;
    v_owner_id    uuid;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    if p_accept is null then
        raise exception 'accept or decline' using errcode = '22023';
    end if;
    v_email := private.confirmed_email(v_uid);
    select i.wishlist_id, i.owner_user_id into v_wishlist_id, v_owner_id
    from public.wishlist_recipient_invites i
    join public.wishlists l on l.id = i.wishlist_id and l.owner_user_id = i.owner_user_id and l.deleted_at is null
    where i.id = p_invite_id and i.recipient_email = v_email and i.status = 'pending' and i.expires_at > now();
    if not found or v_owner_id = v_uid then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    if not p_accept then
        update public.wishlist_recipient_invites set status = 'declined', closed_at = now()
        where id = p_invite_id and status = 'pending' and expires_at > now();
        if not found then
            raise exception 'not available' using errcode = 'P0002';
        end if;
        return '{}'::jsonb;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(1734960756, private.wishlist_capacity_key(v_wishlist_id));

    -- A revocation, a decline elsewhere or a deletion committed meanwhile wins.
    update public.wishlist_recipient_invites set status = 'accepted', closed_at = now()
    where id = p_invite_id and status = 'pending' and expires_at > now()
      and exists (select 1 from public.wishlists l where l.id = v_wishlist_id and l.deleted_at is null);
    if not found then
        raise exception 'not available' using errcode = 'P0002';
    end if;
    if not exists (select 1 from public.wishlist_members m where m.wishlist_id = v_wishlist_id and m.member_user_id = v_uid) then
        if (select count(*) from public.wishlist_members m where m.wishlist_id = v_wishlist_id) >= 50 then
            raise exception 'not available' using errcode = 'P0002';
        end if;
        insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id)
        values (v_wishlist_id, v_owner_id, v_uid)
        on conflict (wishlist_id, member_user_id) do nothing;
    end if;
    return jsonb_build_object('wishlist_id', v_wishlist_id);
end;
$$;

revoke all on function public.create_recipient_invite(uuid, text) from public, anon, authenticated, service_role;
revoke all on function public.list_recipient_invites(uuid) from public, anon, authenticated, service_role;
revoke all on function public.revoke_recipient_invite(uuid) from public, anon, authenticated, service_role;
revoke all on function public.get_my_invites() from public, anon, authenticated, service_role;
revoke all on function public.respond_to_invite(uuid, boolean) from public, anon, authenticated, service_role;

grant execute on function public.create_recipient_invite(uuid, text) to authenticated;
grant execute on function public.list_recipient_invites(uuid) to authenticated;
grant execute on function public.revoke_recipient_invite(uuid) to authenticated;
grant execute on function public.get_my_invites() to authenticated;
grant execute on function public.respond_to_invite(uuid, boolean) to authenticated;

-- ---------------------------------------------------------------------------------------------
-- Account deletion: the account's own recipient invites go with its lists. Invites addressed to
-- it stay as the owners wrote them: removing them would tell an owner the address had an account.
-- The body is 20261024120000's with that one delete added.
-- ---------------------------------------------------------------------------------------------

create or replace function private.delete_account(p_user_id uuid)
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
    delete from public.wishlist_recipient_invites where owner_user_id = p_user_id;

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
