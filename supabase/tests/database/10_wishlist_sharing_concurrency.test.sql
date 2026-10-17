-- Sharing and reservations on two real connections (dblink), as in 07_sync_concurrency:
--   * two members reserving one wish: exactly one wins, whichever order the commits come in;
--   * a stale reservation taken over by two members at once: exactly one wins;
--   * the owner never waits for a reservation: guest calls fail at once, and writes to the
--     reserved wish, its deletion and removing the reserver all complete while it is held;
--   * invite and member limits hold under concurrency; the same user joining twice at once gets
--     one membership; unrelated lists, reservations and unauthorised callers never wait on the
--     capacity lock.
-- Fixtures are committed through their own connection and deleted at the end. The connections
-- log in as postgres and act as users through set role authenticated and request.jwt.claims.
begin;
create extension if not exists pgtap with schema extensions;
create extension if not exists dblink with schema extensions;
select plan(30);

create temporary table fixture as select
    gen_random_uuid() as alice, gen_random_uuid() as bob, gen_random_uuid() as carol,
    gen_random_uuid() as xavier, gen_random_uuid() as yusuf, gen_random_uuid() as zoe,
    gen_random_uuid() as list_res, gen_random_uuid() as list_cap, gen_random_uuid() as list_mem,
    gen_random_uuid() as list_same, gen_random_uuid() as list_other,
    gen_random_uuid() as wish_race, gen_random_uuid() as wish_rollback, gen_random_uuid() as wish_stale,
    gen_random_uuid() as wish_key, gen_random_uuid() as wish_deleted, gen_random_uuid() as wish_free,
    decode(repeat('a1', 32), 'hex') as raw_mem, decode(repeat('b2', 32), 'hex') as raw_same,
    format('host=%s port=%s dbname=%s user=postgres password=postgres',
           host(inet_server_addr()), inet_server_port(), current_database()) as connection;

select extensions.dblink_connect('setup', connection) from fixture;
select extensions.dblink_connect('first', connection) from fixture;
select extensions.dblink_connect('second', connection) from fixture;
select extensions.dblink_exec('first', 'set role authenticated');
select extensions.dblink_exec('second', 'set role authenticated');

create function pg_temp.token(raw bytea) returns text language sql as $$
    select rtrim(translate(encode(raw, 'base64'), '+/', '-_'), '=')
$$;
create function pg_temp.act(conn text, id uuid) returns text language sql as $$
    select extensions.dblink_exec(conn, format(
        'set request.jwt.claims = %L', json_build_object('sub', id, 'role', 'authenticated')::text))
$$;
create function pg_temp.text_of(conn text, sql text) returns text language sql as $$
    select t.x from extensions.dblink(conn, sql) as t(x text)
$$;
-- The first line of an error dblink reports, without its "ERROR:" prefix.
create function pg_temp.message(raw text) returns text language sql as $$
    select regexp_replace(split_part(raw, chr(10), 1), '^ERROR:\s+', '')
$$;
-- Runs `sql` on `conn` and returns its error message, or 'ok'. A select is run as a DO block,
-- since dblink_exec refuses statements that return rows.
create function pg_temp.outcome(conn text, sql text) returns text language plpgsql as $$
declare
    v_sql text := case when sql ilike 'select %' then 'do $o$ begin perform ' || substr(sql, 8) || '; end $o$' else sql end;
begin
    if extensions.dblink_exec(conn, v_sql, false) = 'ERROR' then
        return pg_temp.message(extensions.dblink_error_message(conn));
    end if;
    return 'ok';
end;
$$;
-- Sends `sql` on the second connection and reports whether it is waiting.
create function pg_temp.send_and_wait(sql text) returns int language plpgsql as $$
begin
    perform extensions.dblink_send_query('second', sql);
    perform pg_sleep(0.3);
    return extensions.dblink_is_busy('second');
end;
$$;
create function pg_temp.result_text() returns text language plpgsql as $$
declare
    v text;
begin
    select t.x into v from extensions.dblink_get_result('second', false) as t(x text);
    if v is null then
        v := pg_temp.message(extensions.dblink_error_message('second'));
    end if;
    perform extensions.dblink_get_result('second', false);  -- clears the connection
    return v;
end;
$$;

-- ---- committed fixtures ----

select extensions.dblink_exec('setup', format(
    $$insert into auth.users (id, email) values (%L, %L), (%L, %L), (%L, %L), (%L, %L), (%L, %L), (%L, %L)$$,
    alice, alice || '@example.test', bob, bob || '@example.test', carol, carol || '@example.test',
    xavier, xavier || '@example.test', yusuf, yusuf || '@example.test', zoe, zoe || '@example.test')) from fixture;
select extensions.dblink_exec('setup', format(
    $$insert into public.wishlists (id, owner_user_id, name, last_mutation_id) values
        (%L, %L, 'Reservations', gen_random_uuid()), (%L, %L, 'Invite cap', gen_random_uuid()),
        (%L, %L, 'Member cap', gen_random_uuid()), (%L, %L, 'Same user', gen_random_uuid()),
        (%L, %L, 'Unrelated', gen_random_uuid())$$,
    list_res, alice, list_cap, alice, list_mem, alice, list_same, alice, list_other, alice)) from fixture;
select extensions.dblink_exec('setup', format(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name, last_mutation_id) values
        (%L, %L, %L, 'Race', gen_random_uuid()), (%L, %L, %L, 'Rollback', gen_random_uuid()),
        (%L, %L, %L, 'Stale', gen_random_uuid()), (%L, %L, %L, 'Key change', gen_random_uuid()),
        (%L, %L, %L, 'Deleted', gen_random_uuid()), (%L, %L, %L, 'Free', gen_random_uuid())$$,
    wish_race, alice, list_res, wish_rollback, alice, list_res, wish_stale, alice, list_res,
    wish_key, alice, list_res, wish_deleted, alice, list_res, wish_free, alice, list_res)) from fixture;
select extensions.dblink_exec('setup', format(
    $$insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id) values (%L, %L, %L), (%L, %L, %L)$$,
    list_res, alice, bob, list_res, alice, carol)) from fixture;
-- A stale reservation: its membership id belongs to no membership.
select extensions.dblink_exec('setup', format(
    $$insert into public.wish_reservations (wish_id, wishlist_id, reserver_user_id, membership_id) values (%L, %L, %L, gen_random_uuid())$$,
    wish_stale, list_res, bob)) from fixture;
-- Four active invites on the invite-cap list.
select extensions.dblink_exec('setup', format(
    $$insert into public.wishlist_invites (wishlist_id, owner_user_id, token_hash, expires_at)
      select %L, %L, pg_catalog.sha256(extensions.gen_random_bytes(32)), now() + interval '7 days' from generate_series(1, 4)$$,
    list_cap, alice)) from fixture;
-- 49 members and a known invite on the member-cap list; a known invite on the same-user list.
select extensions.dblink_exec('setup', format(
    $$with fillers as (
          insert into auth.users (id, email)
          select gen_random_uuid(), %L || n || '@example.test' from generate_series(1, 49) n returning id)
      insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id) select %L, %L, id from fillers$$,
    'filler-' || list_mem || '-', list_mem, alice)) from fixture;
select extensions.dblink_exec('setup', format(
    $$insert into public.wishlist_invites (wishlist_id, owner_user_id, token_hash, expires_at) values
        (%L, %L, pg_catalog.sha256(%L::bytea), now() + interval '7 days'),
        (%L, %L, pg_catalog.sha256(%L::bytea), now() + interval '7 days')$$,
    list_mem, alice, raw_mem, list_same, alice, raw_same)) from fixture;

-- ---- 1. Two members reserve one wish; the first commits ----

select pg_temp.act('first', bob) from fixture;
select pg_temp.act('second', carol) from fixture;
select extensions.dblink_exec('first', 'begin');
select is((select pg_temp.text_of('first', format('select public.reserve_wish(%L)', wish_race)) from fixture),
    'RESERVED_BY_ME', 'the first member reserves the wish');
select is((select pg_temp.send_and_wait(format('select public.reserve_wish(%L)', wish_race)) from fixture), 1,
    'the second member''s reservation waits for the first');
select extensions.dblink_exec('first', 'commit');
select is(pg_temp.result_text(), 'RESERVED_BY_OTHER', 'once the first commits, the second learns it is taken');
select is((select count(*)::int from fixture, extensions.dblink('setup', format(
              'select 1 from public.wish_reservations where wish_id = %L', wish_race)) as t(x int) ), 1,
    'there is exactly one reservation');

-- ---- 2. Two members reserve one wish; the first rolls back ----

select extensions.dblink_exec('first', 'begin');
select pg_temp.text_of('first', format('select public.reserve_wish(%L)', wish_rollback)) from fixture;
select is((select pg_temp.send_and_wait(format('select public.reserve_wish(%L)', wish_rollback)) from fixture), 1,
    'the second waits while the first is undecided');
select extensions.dblink_exec('first', 'rollback');
select is(pg_temp.result_text(), 'RESERVED_BY_ME', 'once the first rolls back, the second gets the wish');

-- ---- 3. Two members take over one stale reservation ----

select extensions.dblink_exec('first', 'begin');
select is((select pg_temp.text_of('first', format('select public.reserve_wish(%L)', wish_stale)) from fixture),
    'RESERVED_BY_ME', 'the first member takes over the stale reservation');
select is((select pg_temp.send_and_wait(format('select public.reserve_wish(%L)', wish_stale)) from fixture), 1,
    'the second waits on the reservation row');
select extensions.dblink_exec('first', 'commit');
select is(pg_temp.result_text(), 'RESERVED_BY_OTHER', 'and then finds it taken: one winner');

-- ---- 4. The owner never waits for a reservation ----

-- Bob holds uncommitted reservations of two wishes; Alice acts with a short lock timeout.
select extensions.dblink_exec('first', 'begin');
select pg_temp.text_of('first', format('select public.reserve_wish(%L)', wish_key)) from fixture;
select pg_temp.text_of('first', format('select public.reserve_wish(%L)', wish_deleted)) from fixture;
select pg_temp.act('second', alice) from fixture;
select extensions.dblink_exec('second', $$set lock_timeout = '200ms'$$);
select is((select pg_temp.outcome('second', format('select public.reserve_wish(%L)', wish_key)) from fixture),
    'not available', 'the owner''s reserve call fails at once, not after waiting');
select is((select pg_temp.outcome('second', format('select public.release_wish(%L)', wish_key)) from fixture),
    'not available', 'so does release');
select is((select pg_temp.outcome('second', format('select public.get_shared_wishlist(%L)', list_res)) from fixture),
    'not available', 'and the guest view');
select matches((select pg_temp.outcome('second', format(
              'update public.wishes set id = gen_random_uuid(), last_mutation_id = gen_random_uuid() where id = %L', wish_key)) from fixture),
    '^id of wishes .* cannot be changed$', 'an attempt to change a reserved wish''s key fails at once with its own error, never by waiting');
select is((select pg_temp.outcome('second', format(
              'update public.wishes set wishlist_id = %L, last_mutation_id = gen_random_uuid() where id = %L', list_other, wish_key)) from fixture),
    'ok', 'the owner moves a reserved wish to another list without waiting');
select is((select pg_temp.outcome('second', format(
              'update public.wishes set deleted_at = now(), last_mutation_id = gen_random_uuid() where id = %L', wish_deleted)) from fixture),
    'ok', 'the owner deletes a reserved wish without waiting');
select is((select pg_temp.outcome('second', format('select public.remove_wishlist_member(%L, %L)', list_res, bob)) from fixture),
    'ok', 'the owner removes the reserving member without waiting');
select extensions.dblink_exec('first', 'rollback');
select extensions.dblink_exec('second', 'set lock_timeout = 0');

-- ---- 5. Invite cap under concurrency: 4 active, two creates at once ----

select pg_temp.act('first', alice) from fixture;
select extensions.dblink_exec('first', 'begin');
select isnt((select pg_temp.text_of('first', format('select public.create_wishlist_invite(%L)::text', list_cap)) from fixture),
    null, 'the first create makes the fifth invite');
select is((select pg_temp.send_and_wait(format('select public.create_wishlist_invite(%L)::text', list_cap)) from fixture), 1,
    'a concurrent create waits for the list''s capacity lock');
select extensions.dblink_exec('first', 'commit');
select is(pg_temp.result_text(), 'invite limit reached', 'and is refused once the first commits');
select is((select count(*)::int from fixture, extensions.dblink('setup', format(
              'select 1 from public.wishlist_invites where wishlist_id = %L and revoked_at is null and expires_at > now()', list_cap))
           as t(x int) ), 5,
    'the list has five active invites, not six');

-- ---- 6. Member cap under concurrency: 49 members, two joins at once ----

select pg_temp.act('first', xavier) from fixture;
select pg_temp.act('second', yusuf) from fixture;
select extensions.dblink_exec('first', 'begin');
select pg_temp.text_of('first', format('select public.redeem_wishlist_invite(%L)::text', pg_temp.token(raw_mem))) from fixture;
select is((select pg_temp.send_and_wait(format('select public.redeem_wishlist_invite(%L)::text', pg_temp.token(raw_mem))) from fixture), 1,
    'a concurrent join waits for the list''s capacity lock');
select extensions.dblink_exec('first', 'commit');
select is(pg_temp.result_text(), 'not available', 'and is refused once the fiftieth member is in');
select is((select count(*)::int from fixture, extensions.dblink('setup', format(
              'select 1 from public.wishlist_members where wishlist_id = %L', list_mem)) as t(x int) ), 50,
    'the list has fifty members, not fifty-one');

-- ---- 7. The same user joining twice at once ----

select pg_temp.act('first', zoe) from fixture;
select pg_temp.act('second', zoe) from fixture;
select extensions.dblink_exec('first', 'begin');
select pg_temp.text_of('first', format('select public.redeem_wishlist_invite(%L)::text', pg_temp.token(raw_same))) from fixture;
select is((select pg_temp.send_and_wait(format('select public.redeem_wishlist_invite(%L) ->> %L', pg_temp.token(raw_same), 'wishlist_id')) from fixture), 1,
    'the second join by the same user waits');
-- The first connection reads its own uncommitted membership with full privileges for a moment.
select extensions.dblink_exec('first', 'reset role');
create temporary table first_membership as
select t.id from fixture, extensions.dblink('first', format(
    'select membership_id::text from public.wishlist_members where wishlist_id = %L and member_user_id = %L', list_same, zoe)) as t(id text);
select extensions.dblink_exec('first', 'set role authenticated');
select extensions.dblink_exec('first', 'commit');
select is((select pg_temp.result_text()), (select list_same::text from fixture), 'and returns the same list');
select is((select count(*)::int from fixture, extensions.dblink('setup', format(
              'select 1 from public.wishlist_members where wishlist_id = %L and member_user_id = %L', list_same, zoe))
           as t(x int) ), 1,
    'there is one membership');
select is((select pg_temp.text_of('setup', format(
              'select membership_id::text from public.wishlist_members where wishlist_id = %L and member_user_id = %L', list_same, zoe))
           from fixture), (select id from first_membership),
    'with the membership id of the first join');

-- ---- 8. Who does not wait on the capacity lock ----

select pg_temp.act('first', alice) from fixture;
select extensions.dblink_exec('first', 'begin');
select pg_temp.text_of('first', format('select public.create_wishlist_invite(%L)::text', list_res)) from fixture;
select extensions.dblink_exec('second', $$set lock_timeout = '200ms'$$);
select pg_temp.act('second', alice) from fixture;
select is((select pg_temp.outcome('second', format('select public.create_wishlist_invite(%L)', list_other)) from fixture), 'ok',
    'an invite on an unrelated list does not wait');
select pg_temp.act('second', carol) from fixture;
select is((select pg_temp.outcome('second', format('select public.reserve_wish(%L)', wish_free)) from fixture), 'ok',
    'a reservation on the locked list does not wait');
select is((select pg_temp.outcome('second', format('select public.create_wishlist_invite(%L)', list_res)) from fixture), 'not available',
    'a non-owner is refused before reaching the lock');
select extensions.dblink_exec('first', 'rollback');

-- ---- clean up the committed fixtures ----

select extensions.dblink_disconnect('first');
select extensions.dblink_disconnect('second');
select extensions.dblink_exec('setup', format($$delete from public.wish_reservations where wishlist_id in (%L, %L)$$, list_res, list_other))
from fixture;
select extensions.dblink_exec('setup', format(
    $$delete from public.wishlist_invites where owner_user_id = %L$$, alice)) from fixture;
select extensions.dblink_exec('setup', format(
    $$delete from public.wishlist_members where owner_user_id = %L$$, alice)) from fixture;
select extensions.dblink_exec('setup', format($$delete from public.wishes where owner_user_id = %L$$, alice)) from fixture;
select extensions.dblink_exec('setup', format($$delete from public.wishlists where owner_user_id = %L$$, alice)) from fixture;
select extensions.dblink_exec('setup', format($$delete from public.sync_mutations where owner_user_id = %L$$, alice)) from fixture;
select extensions.dblink_exec('setup', format(
    $$delete from auth.users where id in (%L, %L, %L, %L, %L, %L) or email like %L$$,
    alice, bob, carol, xavier, yusuf, zoe, 'filler-' || list_mem || '-%')) from fixture;
select extensions.dblink_disconnect('setup');

select * from finish();
rollback;
