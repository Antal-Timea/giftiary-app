-- Sharing, recipient invites: the invited person is told, and the address is kept no longer than
-- the invite is open (GDPR Article 14 and storage limitation). 4B's rules otherwise stand.
--
--   * Each new invite sends one email to its address: a Giftiary user invited you, open the app or
--     register with this address within 14 days, where the address came from, what it is used
--     for, and the privacy policy. The message is the same whether or not the address has an
--     account, and nothing here looks at accounts to send it. It names neither the owner nor the
--     list, so an owner cannot put their own words into it. Inviting again while an invite is
--     pending sends nothing.
--   * The email goes to Resend through pg_net, queued in the same transaction as the invite, so an
--     invite rolled back sends nothing. Without the Vault secret `resend_api_key` nothing is sent;
--     the job below sends what is still unsent once it is set.
--   * When an invite closes (accepted, declined, revoked or expired) its address is erased. The row
--     stays with its list, owner, status and times.
--   * Expiry is no longer only derived: a pg_cron job closes expired invites every minute, erasing
--     their addresses, and retries emails that did not go out.
--   * The owner's list shows pending invites only. Closed ones have no address left to show.

create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron;

update public.wishlist_recipient_invites
set status = 'expired', closed_at = expires_at
where status = 'pending' and expires_at <= now();
update public.wishlist_recipient_invites set recipient_email = null where status <> 'pending';

alter table public.wishlist_recipient_invites
    alter column recipient_email drop not null,
    add constraint wishlist_recipient_invites_email_only_pending
        check ((status = 'pending') = (recipient_email is not null)),
    -- The pg_net request of the email in flight, if any.
    add column email_request_id bigint,
    add column email_sent_at timestamptz,
    add column email_attempts smallint not null default 0;

create index wishlist_recipient_invites_pending_expiry_idx
    on public.wishlist_recipient_invites (expires_at) where status = 'pending';

-- After this many sends without an accepted answer, the invite stays unsent.
create function private.recipient_invite_email_max_attempts()
returns smallint
language sql
immutable
set search_path = ''
as $$ select 5::smallint $$;

revoke all on function private.recipient_invite_email_max_attempts() from public, anon, authenticated, service_role;

-- ---------------------------------------------------------------------------------------------
-- The invite email: only the address and the expiry vary, so no two recipients can be told apart
-- ---------------------------------------------------------------------------------------------

create function private.recipient_invite_email(p_to text, p_expires_at timestamptz)
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object(
        'from', 'Giftiary <no-reply@giftiary.app>',
        'to', jsonb_build_array(p_to),
        'subject', 'Meghívó egy Giftiary-kívánságlistára / Invitation to a Giftiary wishlist',
        'text', format($text$Szia!

Egy Giftiary-felhasználó meghívott egy megosztott kívánságlistájára.

Ha már használod a Giftiaryt, nyisd meg az alkalmazást, és jelentkezz be ezzel az email-címmel: ott vár rád a meghívó. Ha még nem használod, %2$s-ig (14 napon belül) regisztrálj ezzel az email-címmel, és a meghívó megjelenik az alkalmazásban: https://giftiary.app/

Az email-címedet az adta meg, aki meghívott. Csak ehhez a meghívóhoz használjuk, marketingre nem. A meghívóból töröljük, amint elfogadod, elutasítod, visszavonják vagy lejár; ha nem teszel semmit, 14 nap után lejár. Jogalap: a meghívó felhasználó jogos érdeke (GDPR 6. cikk (1) f)). Tiltakozhatsz, és a jogaidat gyakorolhatod a support@giftiary.app címen.

Adatkezelési tájékoztató: https://giftiary.app/privacy/#hu

Ez egyszeri üzenet: erről a meghívóról több emailt nem küldünk.

---

Hello,

A Giftiary user has invited you to a shared wishlist.

If you already use Giftiary, open the app and sign in with this email address: the invitation is waiting there. If you don't use Giftiary yet, register with this email address by %2$s (within 14 days) and the invitation appears in the app: https://giftiary.app/

Your email address was entered by the person who invited you. We use it only for this invitation, not for marketing. We delete it from the invitation as soon as you accept or decline it, it is withdrawn, or it expires; if you do nothing, it expires after 14 days. Legal basis: the inviting user's legitimate interest (GDPR Article 6(1)(f)). You can object and exercise your rights at support@giftiary.app.

Privacy policy: https://giftiary.app/privacy/#en

This is a one-time message: you will not get further emails about this invitation.

Giftiary · %1$s$text$,
            p_to, pg_catalog.to_char(p_expires_at at time zone 'UTC', 'YYYY-MM-DD')));
$$;

revoke all on function private.recipient_invite_email(text, timestamptz) from public, anon, authenticated, service_role;

-- Queues the email of an open, unsent invite, if the API key is set. Returns whether it queued.
create function private.send_recipient_invite_email(p_invite_id uuid)
returns boolean
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_key     text;
    v_invite  public.wishlist_recipient_invites;
    v_request bigint;
begin
    select s.decrypted_secret into v_key from vault.decrypted_secrets s where s.name = 'resend_api_key';
    if v_key is null or v_key = '' then
        return false;
    end if;

    select i.* into v_invite
    from public.wishlist_recipient_invites i
    join public.wishlists l on l.id = i.wishlist_id and l.owner_user_id = i.owner_user_id and l.deleted_at is null
    where i.id = p_invite_id and i.status = 'pending' and i.expires_at > now()
      and i.email_sent_at is null and i.email_request_id is null
      and i.email_attempts < private.recipient_invite_email_max_attempts()
    for update of i;
    if not found then
        return false;
    end if;

    v_request := net.http_post(
        url := 'https://api.resend.com/emails',
        body := private.recipient_invite_email(v_invite.recipient_email, v_invite.expires_at),
        headers := jsonb_build_object(
            'Authorization', 'Bearer ' || v_key,
            'Content-Type', 'application/json',
            -- A retry of the same invite is not delivered twice.
            'Idempotency-Key', 'recipient-invite/' || v_invite.id::text),
        timeout_milliseconds := 10000);
    update public.wishlist_recipient_invites
    set email_request_id = v_request, email_attempts = email_attempts + 1
    where id = v_invite.id;
    return true;
end;
$$;

revoke all on function private.send_recipient_invite_email(uuid) from public, anon, authenticated, service_role;

-- ---------------------------------------------------------------------------------------------
-- The job: close expired invites, settle answered emails, send what is still unsent
-- ---------------------------------------------------------------------------------------------

create function private.expire_recipient_invites()
returns integer
language sql
volatile
security definer
set search_path = ''
as $$
    with closed as (
        update public.wishlist_recipient_invites
        set status = 'expired', closed_at = expires_at, recipient_email = null, email_request_id = null
        where status = 'pending' and expires_at <= now()
        returning 1
    )
    select count(*)::integer from closed;
$$;

revoke all on function private.expire_recipient_invites() from public, anon, authenticated, service_role;

create function private.settle_recipient_invite_emails()
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
    -- Accepted: sent. Refused for good (4xx other than 429): no more attempts. Otherwise sent again.
    update public.wishlist_recipient_invites i
    set email_request_id = null,
        email_sent_at = case when r.status_code between 200 and 299 then r.created end,
        email_attempts = case
            when r.status_code between 400 and 499 and r.status_code <> 429
                then private.recipient_invite_email_max_attempts()
            else i.email_attempts end
    from net._http_response r
    where r.id = i.email_request_id;

    -- Neither waiting nor answered: pg_net lost it (its responses are dropped after a few hours).
    update public.wishlist_recipient_invites i
    set email_request_id = null
    where i.email_request_id is not null
      and not exists (select 1 from net.http_request_queue q where q.id = i.email_request_id)
      and not exists (select 1 from net._http_response r where r.id = i.email_request_id);
end;
$$;

revoke all on function private.settle_recipient_invite_emails() from public, anon, authenticated, service_role;

create function private.run_recipient_invite_jobs()
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_id uuid;
begin
    perform private.expire_recipient_invites();
    perform private.settle_recipient_invite_emails();
    for v_id in
        select i.id from public.wishlist_recipient_invites i
        where i.status = 'pending' and i.expires_at > now()
          and i.email_sent_at is null and i.email_request_id is null
          and i.email_attempts < private.recipient_invite_email_max_attempts()
        order by i.created_at
        limit 50
    loop
        perform private.send_recipient_invite_email(v_id);
    end loop;
end;
$$;

revoke all on function private.run_recipient_invite_jobs() from public, anon, authenticated, service_role;

select cron.schedule('recipient-invites', '* * * * *', 'select private.run_recipient_invite_jobs()');

-- ---------------------------------------------------------------------------------------------
-- The functions of 4B, each closing with the address erased; create also sends the email
-- ---------------------------------------------------------------------------------------------

create or replace function public.create_recipient_invite(p_wishlist_id uuid, p_email text)
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
        update public.wishlist_recipient_invites
        set status = 'expired', closed_at = v_invite.expires_at, recipient_email = null, email_request_id = null
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
    perform private.send_recipient_invite_email(v_invite.id);
    return jsonb_build_object(
        'invite_id', v_invite.id, 'recipient_email', v_invite.recipient_email, 'state', 'PENDING',
        'created_at', v_invite.created_at, 'expires_at', v_invite.expires_at);
end;
$$;

-- The caller's list's open invites, oldest first. `state` stays for the clients that read it.
create or replace function public.list_recipient_invites(p_wishlist_id uuid)
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
                   'state', 'PENDING',
                   'created_at', i.created_at,
                   'expires_at', i.expires_at)
               order by i.created_at, i.id)
        from public.wishlist_recipient_invites i
        where i.wishlist_id = p_wishlist_id and i.status = 'pending' and i.expires_at > now()
    ), '[]'::jsonb);
end;
$$;

-- Withdraws a pending invite. A closed one is left as it is.
create or replace function public.revoke_recipient_invite(p_invite_id uuid)
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
    update public.wishlist_recipient_invites
    set status = 'revoked', closed_at = now(), recipient_email = null, email_request_id = null
    where id = p_invite_id and owner_user_id = v_uid and status = 'pending';
end;
$$;

create or replace function public.respond_to_invite(p_invite_id uuid, p_accept boolean)
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
        update public.wishlist_recipient_invites
        set status = 'declined', closed_at = now(), recipient_email = null, email_request_id = null
        where id = p_invite_id and status = 'pending' and expires_at > now();
        if not found then
            raise exception 'not available' using errcode = 'P0002';
        end if;
        return '{}'::jsonb;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(1734960756, private.wishlist_capacity_key(v_wishlist_id));

    -- A revocation, a decline elsewhere or a deletion committed meanwhile wins.
    update public.wishlist_recipient_invites
    set status = 'accepted', closed_at = now(), recipient_email = null, email_request_id = null
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
