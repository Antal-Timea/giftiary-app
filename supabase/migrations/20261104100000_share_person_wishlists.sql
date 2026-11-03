-- Sharing, phase 2B: a list kept for a person can be shared like the user's own lists.
--
-- W3 kept every sharing function to lists with person_id null. The function results never carry
-- the person: members get the list's name, its owner's display name and its wishes' display
-- fields, and every table holding the person, occasions, gifts and ideas stays owner-only. So the
-- guard is dropped and nothing else changes: the bodies below are W3's, without
-- "and l.person_id is null". create or replace keeps every function's owner and privileges.

create or replace function private.active_reservation_holder(p_wish_id uuid)
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
        on l.id = w.wishlist_id and l.deleted_at is null
    join public.wishlist_members m
        on m.wishlist_id = r.wishlist_id
       and m.member_user_id = r.reserver_user_id
       and m.membership_id = r.membership_id
    where r.wish_id = p_wish_id
$$;

create or replace function public.create_wishlist_invite(p_wishlist_id uuid, p_valid_days integer default 7)
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
        where l.id = p_wishlist_id and l.owner_user_id = v_uid and l.deleted_at is null
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

create or replace function public.redeem_wishlist_invite(p_token text)
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
      and l.deleted_at is null;
    if not found or v_owner_id = v_uid then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(1734960756, private.wishlist_capacity_key(v_wishlist_id));

    -- A revocation committed while this call waited for the lock wins.
    if not exists (
        select 1 from public.wishlist_invites i
        join public.wishlists l on l.id = i.wishlist_id
        where i.id = v_invite_id and i.revoked_at is null and i.expires_at > now()
          and l.deleted_at is null
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
                   'wishlist_id', l.id, 'name', l.name, 'owner_display_name', p.display_name)
               order by l.name, l.id)
        from public.wishlist_members m
        join public.wishlists l on l.id = m.wishlist_id and l.deleted_at is null
        left join public.profiles p on p.id = l.owner_user_id
        where m.member_user_id = v_uid
    ), '[]'::jsonb);
end;
$$;

create or replace function public.get_shared_wishlist(p_wishlist_id uuid)
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
    where l.id = p_wishlist_id and l.deleted_at is null;
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

create or replace function public.reserve_wish(p_wish_id uuid)
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
    where w.id = p_wish_id and w.deleted_at is null and l.deleted_at is null;
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

create or replace function public.release_wish(p_wish_id uuid)
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
    where w.id = p_wish_id and w.deleted_at is null and l.deleted_at is null;
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
