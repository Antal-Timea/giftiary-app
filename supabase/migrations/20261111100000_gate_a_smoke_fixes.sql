-- Gate A smoke fixes (backend part).
--
--   1. The owner of a list kept for someone else can learn which of its wishes are reserved, never
--      by whom: they buy for that person too, and must not buy the same thing. The owner of their own
--      list still learns nothing, so the surprise holds. W3's rule (the owner never learns whether a
--      wish is reserved) now covers own lists only; for a person's list the owner is told a plain
--      yes per wish, which is all a member who did not reserve it sees too.
--   2. An email invite to someone who is already a member of the list is refused before an invite
--      or an email exists. The owner sees their members already, so saying so tells them nothing
--      about anyone else.
--   3. A new account takes its first display name from the sign-in provider's name when Giftiary
--      was not given one. Only creation looks at it: later sign-ins and linked identities never
--      touch a profile again.
--   4. The invite email gets an HTML part. The plain text part, its meaning and its links stay; the
--      HTML holds no images or remote resources, so opening it tells nobody anything.

-- ---------------------------------------------------------------------------------------------
-- 1. Reserved wishes of an owner's list kept for a person
-- ---------------------------------------------------------------------------------------------

-- The ids of the reserved wishes of the caller's own active list kept for a person. A list of the
-- caller's own, or anything else, is not available: the owner of their own list is never told.
create function public.get_owner_reserved_wishes(p_wishlist_id uuid)
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
          and l.person_id is not null
    ) then
        raise exception 'not available' using errcode = 'P0002';
    end if;

    return coalesce((
        select jsonb_agg(w.id order by w.id)
        from public.wishes w
        where w.wishlist_id = p_wishlist_id and w.deleted_at is null
          and private.active_reservation_holder(w.id) is not null
    ), '[]'::jsonb);
end;
$$;

revoke all on function public.get_owner_reserved_wishes(uuid) from public, anon, authenticated, service_role;
grant execute on function public.get_owner_reserved_wishes(uuid) to authenticated;

-- ---------------------------------------------------------------------------------------------
-- 2. No email invite for an existing member
-- ---------------------------------------------------------------------------------------------

-- 20261110100000's function, refusing a member's confirmed address with 23505 after the locks, so
-- a join committed meanwhile is seen.
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

    -- Only this list's members are compared, and the owner already sees who they are.
    if exists (
        select 1 from public.wishlist_members m
        where m.wishlist_id = p_wishlist_id and private.confirmed_email(m.member_user_id) = v_email
    ) then
        raise exception 'already a member' using errcode = '23505';
    end if;

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

-- ---------------------------------------------------------------------------------------------
-- 3. First display name from the sign-in provider
-- ---------------------------------------------------------------------------------------------

-- Giftiary's own display_name first; then the provider's full_name or name (Google and Facebook
-- send both). Runs only on insert, so a name edited later is never replaced. Line breaks and other
-- control characters become spaces, as the app accepts none in a name.
create or replace function private.create_profile_for_new_user()
    returns trigger
    language plpgsql
    security definer
    set search_path = ''
as $$
begin
    insert into public.profiles (id, display_name)
    values (new.id, coalesce(
        nullif(btrim(regexp_replace(new.raw_user_meta_data ->> 'display_name', '[[:cntrl:]]+', ' ', 'g')), ''),
        nullif(btrim(regexp_replace(new.raw_user_meta_data ->> 'full_name', '[[:cntrl:]]+', ' ', 'g')), ''),
        nullif(btrim(regexp_replace(new.raw_user_meta_data ->> 'name', '[[:cntrl:]]+', ' ', 'g')), '')))
    -- Safe if the trigger ever runs twice for the same user.
    on conflict (id) do nothing;
    return new;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- 4. The invite email with an HTML part
-- ---------------------------------------------------------------------------------------------

create function private.html_escape(p_text text)
returns text
language sql
immutable
set search_path = ''
as $$
    select replace(replace(replace(replace(replace(p_text, '&', '&amp;'), '<', '&lt;'), '>', '&gt;'), '"', '&quot;'), '''', '&#39;')
$$;

revoke all on function private.html_escape(text) from public, anon, authenticated, service_role;

-- The text part is 20261110100000's, unchanged. The HTML says the same in the same order, with
-- tables and inline styles only, for the mail clients that ignore anything else.
create or replace function private.recipient_invite_email(p_to text, p_expires_at timestamptz)
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
            p_to, pg_catalog.to_char(p_expires_at at time zone 'UTC', 'YYYY-MM-DD')),
        'html', format($html$<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light">
<title>Giftiary</title>
</head>
<body style="margin:0;padding:0;background-color:#FBF7EE;">
<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FBF7EE;">
<tr><td align="center" style="padding:24px 16px;">
<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;font-family:Arial,Helvetica,sans-serif;color:#17423E;">
<tr><td style="padding:8px 0 20px 0;font-family:Georgia,'Times New Roman',serif;font-size:26px;color:#17423E;">Giftiary</td></tr>
<tr><td style="background-color:#FFFFFF;border:1px solid #E3E8E4;border-radius:12px;padding:28px 24px;">
<p style="margin:0 0 8px 0;font-family:Georgia,'Times New Roman',serif;font-size:22px;line-height:28px;color:#17423E;">Meghívtak egy kívánságlistára</p>
<p style="margin:0 0 16px 0;font-size:15px;line-height:22px;">Szia! Egy Giftiary-felhasználó meghívott egy megosztott kívánságlistájára.</p>
<p style="margin:0 0 20px 0;font-size:15px;line-height:22px;">Ha már használod a Giftiaryt, nyisd meg az alkalmazást, és jelentkezz be ezzel az email-címmel: ott vár rád a meghívó. Ha még nem használod, <strong>%2$s-ig</strong> (14 napon belül) regisztrálj ezzel az email-címmel, és a meghívó megjelenik az alkalmazásban.</p>
<table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr><td style="border-radius:24px;background-color:#D6285A;"><a href="https://giftiary.app/" style="display:inline-block;padding:12px 24px;font-size:15px;font-weight:bold;color:#FFFFFF;text-decoration:none;border-radius:24px;">Giftiary megnyitása</a></td></tr></table>
<p style="margin:20px 0 0 0;font-size:13px;line-height:19px;color:#47615D;">Az email-címedet az adta meg, aki meghívott. Csak ehhez a meghívóhoz használjuk, marketingre nem. A meghívóból töröljük, amint elfogadod, elutasítod, visszavonják vagy lejár; ha nem teszel semmit, 14 nap után lejár. Jogalap: a meghívó felhasználó jogos érdeke (GDPR 6. cikk (1) f)). Tiltakozhatsz, és a jogaidat gyakorolhatod a <a href="mailto:support@giftiary.app" style="color:#17423E;">support@giftiary.app</a> címen. <a href="https://giftiary.app/privacy/#hu" style="color:#17423E;">Adatkezelési tájékoztató</a></p>
<p style="margin:12px 0 0 0;font-size:13px;line-height:19px;color:#47615D;">Ez egyszeri üzenet: erről a meghívóról több emailt nem küldünk.</p>
</td></tr>
<tr><td style="height:16px;font-size:0;line-height:0;">&nbsp;</td></tr>
<tr><td lang="en" style="background-color:#FFFFFF;border:1px solid #E3E8E4;border-radius:12px;padding:28px 24px;">
<p style="margin:0 0 8px 0;font-family:Georgia,'Times New Roman',serif;font-size:22px;line-height:28px;color:#17423E;">You're invited to a wishlist</p>
<p style="margin:0 0 16px 0;font-size:15px;line-height:22px;">Hello, a Giftiary user has invited you to a shared wishlist.</p>
<p style="margin:0 0 20px 0;font-size:15px;line-height:22px;">If you already use Giftiary, open the app and sign in with this email address: the invitation is waiting there. If you don't use Giftiary yet, register with this email address <strong>by %2$s</strong> (within 14 days) and the invitation appears in the app.</p>
<table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr><td style="border-radius:24px;background-color:#D6285A;"><a href="https://giftiary.app/" style="display:inline-block;padding:12px 24px;font-size:15px;font-weight:bold;color:#FFFFFF;text-decoration:none;border-radius:24px;">Open Giftiary</a></td></tr></table>
<p style="margin:20px 0 0 0;font-size:13px;line-height:19px;color:#47615D;">Your email address was entered by the person who invited you. We use it only for this invitation, not for marketing. We delete it from the invitation as soon as you accept or decline it, it is withdrawn, or it expires; if you do nothing, it expires after 14 days. Legal basis: the inviting user's legitimate interest (GDPR Article 6(1)(f)). You can object and exercise your rights at <a href="mailto:support@giftiary.app" style="color:#17423E;">support@giftiary.app</a>. <a href="https://giftiary.app/privacy/#en" style="color:#17423E;">Privacy policy</a></p>
<p style="margin:12px 0 0 0;font-size:13px;line-height:19px;color:#47615D;">This is a one-time message: you will not get further emails about this invitation.</p>
</td></tr>
<tr><td style="padding:16px 4px 0 4px;font-size:12px;line-height:18px;color:#547370;">Giftiary · %1$s · <a href="https://giftiary.app/" style="color:#547370;">giftiary.app</a></td></tr>
</table>
</td></tr>
</table>
</body>
</html>$html$,
            private.html_escape(p_to), pg_catalog.to_char(p_expires_at at time zone 'UTC', 'YYYY-MM-DD')));
$$;
