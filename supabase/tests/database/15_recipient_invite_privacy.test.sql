-- Recipient invite privacy: each new invite queues one email that is the same for any address,
-- and every way an invite closes erases its address, expiry included, done by the job itself.

begin;
create extension if not exists pgtap with schema extensions;
select plan(41);

-- ---- schema and privileges ----

select col_is_null('public', 'wishlist_recipient_invites', 'recipient_email', 'a closed invite keeps no address');
select ok(exists (select 1 from pg_constraint where conname = 'wishlist_recipient_invites_email_only_pending'),
    'and only a pending one has one');
select ok(
    (select bool_and(not has_function_privilege(r, f, 'execute'))
     from unnest(array['anon', 'authenticated', 'service_role']) r,
          unnest(array['private.send_recipient_invite_email(uuid)', 'private.run_recipient_invite_jobs()',
                       'private.expire_recipient_invites()', 'private.settle_recipient_invite_emails()',
                       'private.recipient_invite_email(text,timestamp with time zone)']) f),
    'no client can send, compose or run the jobs');
select is((select schedule from cron.job where jobname = 'recipient-invites'), '* * * * *', 'the job runs every minute');
select is((select command from cron.job where jobname = 'recipient-invites'), 'select private.run_recipient_invite_jobs()',
    'and runs the invite jobs');
select ok(
    (select bool_and(prosrc !~* 'auth\.users') from pg_proc
     where oid in ('private.send_recipient_invite_email(uuid)'::regprocedure, 'private.run_recipient_invite_jobs()'::regprocedure,
                   'private.recipient_invite_email(text,timestamp with time zone)'::regprocedure)),
    'sending never looks at accounts');

-- ---- fixtures ----

insert into auth.users (id, email, email_confirmed_at, raw_user_meta_data) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test', now(), '{"display_name": "Alice"}'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test', now(), '{}'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'carol@example.test', now(), '{}');
insert into public.wishlists (id, owner_user_id, name, last_mutation_id) values
    ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Birthday "secret" plans', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Soon deleted', gen_random_uuid());

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
create function pg_temp.invite(list uuid, email text) returns uuid language sql as $$
    select (public.create_recipient_invite(list, email) ->> 'invite_id')::uuid
$$;
create function pg_temp.row_of(id uuid) returns public.wishlist_recipient_invites language sql as $$
    select * from public.wishlist_recipient_invites where wishlist_recipient_invites.id = $1
$$;
-- The queued email of an invite, with its address and date replaced, to compare two of them.
create function pg_temp.queued(id uuid) returns net.http_request_queue language sql as $$
    select q.* from net.http_request_queue q where q.id = (pg_temp.row_of($1)).email_request_id
$$;
create function pg_temp.body(id uuid) returns jsonb language sql as $$
    select convert_from((pg_temp.queued($1)).body, 'UTF8')::jsonb
$$;
create function pg_temp.masked(id uuid) returns text language sql as $$
    select replace(replace(pg_temp.body($1)::text, (pg_temp.body($1) -> 'to' ->> 0), '<to>'),
                   to_char((pg_temp.row_of($1)).expires_at at time zone 'UTC', 'YYYY-MM-DD'), '<date>')
$$;
grant execute on function pg_temp.invite(uuid, text) to authenticated;

-- ---- without the API key nothing is sent, and the invite is made all the same ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select set_config('test.early', pg_temp.invite('10000000-0000-4000-8000-000000000001', 'early@example.test')::text, true);
reset role;
select is((pg_temp.row_of(current_setting('test.early')::uuid)).email_request_id, null, 'without the key nothing is queued');
select is((select count(*)::int from net.http_request_queue), 0, 'and nothing waits to be sent');

select vault.create_secret('re_test_key', 'resend_api_key');

-- ---- creating an invite queues one email ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
-- Bob has an account; nobody@ has none.
select set_config('test.bob', pg_temp.invite('10000000-0000-4000-8000-000000000001', 'Bob@Example.test ')::text, true);
select set_config('test.nobody', pg_temp.invite('10000000-0000-4000-8000-000000000001', 'nobody@example.test')::text, true);
select is(pg_temp.invite('10000000-0000-4000-8000-000000000001', 'bob@example.test'), current_setting('test.bob')::uuid,
    'inviting again while pending returns the same invite');
reset role;

select is((select count(*)::int from net.http_request_queue), 2, 'one email per new invite, none for the repeat');
select is((pg_temp.queued(current_setting('test.bob')::uuid)).url, 'https://api.resend.com/emails', 'sent through Resend');
select is((pg_temp.queued(current_setting('test.bob')::uuid)).method, 'POST', 'as a POST');
select is((pg_temp.queued(current_setting('test.bob')::uuid)).headers ->> 'Authorization', 'Bearer re_test_key',
    'with the key from Vault');
select is((pg_temp.queued(current_setting('test.bob')::uuid)).headers ->> 'Idempotency-Key',
    'recipient-invite/' || current_setting('test.bob'), 'keyed by the invite, so a retry is not delivered twice');
select is(pg_temp.body(current_setting('test.bob')::uuid) -> 'to', '["bob@example.test"]'::jsonb, 'to the normalized address');
select is(pg_temp.body(current_setting('test.bob')::uuid) ->> 'from', 'Giftiary <no-reply@giftiary.app>', 'from no-reply');
select is((pg_temp.row_of(current_setting('test.bob')::uuid)).email_attempts, 1::smallint, 'counted as one attempt');

select is(pg_temp.masked(current_setting('test.bob')::uuid), pg_temp.masked(current_setting('test.nobody')::uuid),
    'an address with an account gets exactly the email an address without one gets');
select ok(pg_temp.body(current_setting('test.bob')::uuid) ->> 'text' !~ '(Alice|alice@|Birthday|secret)',
    'the email names neither the owner nor the list');
select ok(pg_temp.body(current_setting('test.bob')::uuid) ->> 'text' ~ 'https://giftiary\.app/privacy/#en'
      and pg_temp.body(current_setting('test.bob')::uuid) ->> 'text' ~ 'https://giftiary\.app/privacy/#hu',
    'it links the privacy policy in both languages');
select ok(pg_temp.body(current_setting('test.bob')::uuid) ->> 'text'
          ~ to_char((pg_temp.row_of(current_setting('test.bob')::uuid)).expires_at at time zone 'UTC', 'YYYY-MM-DD'),
    'and says until when to register');
select ok(pg_temp.body(current_setting('test.bob')::uuid) ->> 'text' ~ 'not for marketing'
      and pg_temp.body(current_setting('test.bob')::uuid) ->> 'text' ~ 'entered by the person who invited you',
    'and where the address came from and what it is used for');

-- ---- the job: unsent invites go out, answered ones are settled ----

select private.run_recipient_invite_jobs();
select isnt((pg_temp.row_of(current_setting('test.early')::uuid)).email_request_id, null,
    'the job sends an invite made before the key was set');
select is((select count(*)::int from net.http_request_queue), 3, 'and does not send the others again');

insert into net._http_response (id, status_code, created)
values ((pg_temp.row_of(current_setting('test.bob')::uuid)).email_request_id, 200, now()),
       ((pg_temp.row_of(current_setting('test.nobody')::uuid)).email_request_id, 503, now()),
       ((pg_temp.row_of(current_setting('test.early')::uuid)).email_request_id, 422, now());
select private.settle_recipient_invite_emails();
select isnt((pg_temp.row_of(current_setting('test.bob')::uuid)).email_sent_at, null, 'an accepted email is sent');
select ok((pg_temp.row_of(current_setting('test.nobody')::uuid)).email_sent_at is null
      and (pg_temp.row_of(current_setting('test.nobody')::uuid)).email_request_id is null,
    'a failed one is free to go again');
select is((pg_temp.row_of(current_setting('test.early')::uuid)).email_attempts, private.recipient_invite_email_max_attempts(),
    'a refused one is not tried again');
select private.run_recipient_invite_jobs();
select ok((pg_temp.row_of(current_setting('test.nobody')::uuid)).email_request_id is not null
      and (pg_temp.row_of(current_setting('test.bob')::uuid)).email_request_id is null
      and (pg_temp.row_of(current_setting('test.early')::uuid)).email_request_id is null,
    'the job resends only the failed one');

-- ---- every way an invite closes erases its address ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select set_config('test.carol', pg_temp.invite('10000000-0000-4000-8000-000000000001', 'carol@example.test')::text, true);
select set_config('test.revoked', pg_temp.invite('10000000-0000-4000-8000-000000000001', 'revoked@example.test')::text, true);
select set_config('test.expiring', pg_temp.invite('10000000-0000-4000-8000-000000000001', 'expiring@example.test')::text, true);
select set_config('test.lazy', pg_temp.invite('10000000-0000-4000-8000-000000000001', 'lazy@example.test')::text, true);
select set_config('test.gone', pg_temp.invite('10000000-0000-4000-8000-000000000002', 'gone@example.test')::text, true);
select public.revoke_recipient_invite(current_setting('test.revoked')::uuid);

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select public.respond_to_invite(current_setting('test.bob')::uuid, true);
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select public.respond_to_invite(current_setting('test.carol')::uuid, false);
reset role;

select is(((pg_temp.row_of(current_setting('test.bob')::uuid)).status, (pg_temp.row_of(current_setting('test.bob')::uuid)).recipient_email),
    ('accepted'::text, null::text), 'accepting erases the address');
select is(((pg_temp.row_of(current_setting('test.carol')::uuid)).status, (pg_temp.row_of(current_setting('test.carol')::uuid)).recipient_email),
    ('declined'::text, null::text), 'declining erases it');
select is(((pg_temp.row_of(current_setting('test.revoked')::uuid)).status, (pg_temp.row_of(current_setting('test.revoked')::uuid)).recipient_email),
    ('revoked'::text, null::text), 'revoking erases it');
select ok((pg_temp.row_of(current_setting('test.bob')::uuid)).closed_at is not null
      and (pg_temp.row_of(current_setting('test.bob')::uuid)).created_at is not null
      and (pg_temp.row_of(current_setting('test.bob')::uuid)).wishlist_id is not null,
    'a closed invite keeps its list, status and times');

update public.wishlist_recipient_invites
set created_at = now() - interval '15 days', expires_at = now() - interval '1 day'
where id in (current_setting('test.expiring')::uuid, current_setting('test.lazy')::uuid);
select is(private.expire_recipient_invites(), 2, 'the job closes the expired invites');
select is(((pg_temp.row_of(current_setting('test.expiring')::uuid)).status, (pg_temp.row_of(current_setting('test.expiring')::uuid)).recipient_email),
    ('expired'::text, null::text), 'and erases their addresses, with nobody calling anything');
select is((pg_temp.row_of(current_setting('test.expiring')::uuid)).closed_at, (pg_temp.row_of(current_setting('test.expiring')::uuid)).expires_at,
    'closed when it expired');
select is((select count(*)::int from public.wishlist_recipient_invites where status <> 'pending' and recipient_email is not null),
    0, 'no closed invite has an address');

-- The job also runs as the scheduled command.
update public.wishlist_recipient_invites
set created_at = now() - interval '15 days', expires_at = now() - interval '1 day'
where id = current_setting('test.nobody')::uuid;
select private.run_recipient_invite_jobs();
select is((pg_temp.row_of(current_setting('test.nobody')::uuid)).recipient_email, null, 'the scheduled run expires them too');

-- ---- an invite of a deleted list, or one already closed, is never emailed ----

update public.wishlist_recipient_invites set email_request_id = null where id = current_setting('test.gone')::uuid;
update public.wishlists set deleted_at = now(), last_mutation_id = gen_random_uuid() where id = '10000000-0000-4000-8000-000000000002';
select ok(not private.send_recipient_invite_email(current_setting('test.gone')::uuid), 'a deleted list''s invite is not sent');
select ok(not private.send_recipient_invite_email(current_setting('test.revoked')::uuid), 'nor a closed one');

-- ---- the owner sees pending invites only ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select is((select array_agg(x ->> 'recipient_email' order by x ->> 'recipient_email')
           from jsonb_array_elements(public.list_recipient_invites('10000000-0000-4000-8000-000000000001')) x),
    array['early@example.test'], 'the owner sees only the invite still pending');
select ok(not exists (select 1 from jsonb_array_elements(public.list_recipient_invites('10000000-0000-4000-8000-000000000001')) x
                      where x ->> 'state' <> 'PENDING'), 'and never a closed state');
reset role;

select * from finish();
rollback;
