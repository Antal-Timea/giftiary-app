-- Two copies of a write running at the same time, on two real connections (dblink): the first
-- HTTP request can still be executing on the server while the client retries the same mutation.
-- At most one copy commits, the mutation is recorded at most once, and the version moves at most
-- once; a competing mutation against the same version loses cleanly.
--
-- Other connections cannot see this test's uncommitted rows, so the fixtures are committed
-- through their own connection and deleted at the end. The connections log in as postgres (the
-- tables' owner, so RLS does not apply to them); what is tested is row locking and the triggers,
-- which behave the same for every role. Local connections use trust authentication, which dblink
-- refuses for non-superusers, so the connections go through the server's own network address.
begin;
create extension if not exists pgtap with schema extensions;
create extension if not exists dblink with schema extensions;
select plan(18);

create temporary table fixture as select
    gen_random_uuid() as owner_id,
    gen_random_uuid() as person_id,
    gen_random_uuid() as created_person_id,
    gen_random_uuid() as m_same,
    gen_random_uuid() as m_aborted,
    gen_random_uuid() as m_winner,
    gen_random_uuid() as m_loser,
    gen_random_uuid() as m_create,
    format('host=%s port=%s dbname=%s user=postgres password=postgres',
           host(inet_server_addr()), inet_server_port(), current_database()) as connection;

select extensions.dblink_connect('setup', connection) from fixture;
select extensions.dblink_connect('first', connection) from fixture;
select extensions.dblink_connect('second', connection) from fixture;

select extensions.dblink_exec('setup', format(
    $$insert into auth.users (id, email) values (%L, %L)$$, owner_id, owner_id || '@example.test')) from fixture;
select extensions.dblink_exec('setup', format(
    $$insert into public.people (id, owner_user_id, name, last_mutation_id) values (%L, %L, 'Anna', gen_random_uuid())$$,
    person_id, owner_id)) from fixture;

-- Sends `sql` on the second connection while the first still holds the row, and reports whether it
-- is waiting for it. pg_sleep only gives the query time to reach the lock.
create function pg_temp.send_and_wait(sql text) returns int language plpgsql as $$
begin
    perform extensions.dblink_send_query('second', sql);
    perform pg_sleep(0.3);
    return extensions.dblink_is_busy('second');
end;
$$;

create function pg_temp.update_sql(mutation uuid, name text, expected bigint) returns text language sql as $$
    select format(
        $u$update public.people set name = %L, last_mutation_id = %L where id = %L and version = %s returning version$u$,
        name, mutation, (select person_id from fixture), expected)
$$;

create function pg_temp.mutations(mutation uuid) returns int language sql as $$
    select count(*)::int from public.sync_mutations where mutation_id = mutation
$$;

create function pg_temp.person() returns record language sql as $$
    select (name, version) from public.people where id = (select person_id from fixture)
$$;

-- ---- 1. The same mutation twice; the first copy commits ----

select extensions.dblink_exec('first', 'begin');
select is(
    (select extensions.dblink_exec('first', replace(pg_temp.update_sql(m_same, 'First copy', 1), ' returning version', '')) from fixture),
    'UPDATE 1', 'the first copy of a mutation updates the row');
select is((select pg_temp.send_and_wait(pg_temp.update_sql(m_same, 'Second copy', 1)) from fixture), 1,
    'the second copy of the same mutation waits for the first');
select extensions.dblink_exec('first', 'commit');
select is((select count(*)::int from extensions.dblink_get_result('second') as t(version bigint)), 0,
    'once the first copy commits, the second matches no row');
select extensions.dblink_get_result('second');  -- clears the connection for the next query

select is((select pg_temp.mutations(m_same) from fixture), 1, 'the mutation is recorded once');
select is((select version from public.people where id = (select person_id from fixture)), 2::bigint,
    'the version moved once');
select is((select name from public.people where id = (select person_id from fixture)), 'First copy',
    'the committed copy''s change stands');

-- ---- 2. The same mutation twice; the first copy rolls back ----

select extensions.dblink_exec('first', 'begin');
select extensions.dblink_exec('first', replace(pg_temp.update_sql(m_aborted, 'Rolled back', 2), ' returning version', ''))
from fixture;
select is((select pg_temp.send_and_wait(pg_temp.update_sql(m_aborted, 'Retried', 2)) from fixture), 1,
    'a retry waits while the original is still running');
select extensions.dblink_exec('first', 'rollback');
select is((select array_agg(version) from extensions.dblink_get_result('second') as t(version bigint)), array[3::bigint],
    'once the original rolls back, the retry commits instead');
select extensions.dblink_get_result('second');

select is((select pg_temp.mutations(m_aborted) from fixture), 1, 'the mutation is still recorded once');
select is((select name from public.people where id = (select person_id from fixture)), 'Retried',
    'only the retry''s change exists');

-- ---- 3. Two different mutations against the same version ----

select extensions.dblink_exec('first', 'begin');
select extensions.dblink_exec('first', replace(pg_temp.update_sql(m_winner, 'Winner', 3), ' returning version', ''))
from fixture;
select is((select pg_temp.send_and_wait(pg_temp.update_sql(m_loser, 'Loser', 3)) from fixture), 1,
    'a competing writer for the same version waits');
select extensions.dblink_exec('first', 'commit');
select is((select count(*)::int from extensions.dblink_get_result('second') as t(version bigint)), 0,
    'the competing writer matches no row once the winner commits');
select extensions.dblink_get_result('second');

select is((select (pg_temp.mutations(m_winner), pg_temp.mutations(m_loser)) from fixture)::text, '(1,0)',
    'only the winning mutation is recorded');
select is((select version from public.people where id = (select person_id from fixture)), 4::bigint,
    'the version moved once, to the winner''s');

-- ---- 4. The same create twice ----

select extensions.dblink_exec('first', 'begin');
select extensions.dblink_exec('first', format(
    $$insert into public.people (id, owner_user_id, name, last_mutation_id) values (%L, %L, 'Created', %L)$$,
    created_person_id, owner_id, m_create)) from fixture;
select is((select pg_temp.send_and_wait(format(
    $$insert into public.people (id, owner_user_id, name, last_mutation_id) values (%L, %L, 'Created', %L)$$,
    created_person_id, owner_id, m_create)) from fixture), 1,
    'a retried create waits for the original insert of the same id');
select extensions.dblink_exec('first', 'commit');
select is((select count(*)::int from extensions.dblink_get_result('second', false) as t(status text)), 0,
    'once the original create commits, the retry produces no row');
select matches(extensions.dblink_error_message('second'), 'duplicate key',
    'the retried create is rejected as a duplicate');
select extensions.dblink_get_result('second');

select is((select pg_temp.mutations(m_create) from fixture), 1, 'the create is recorded once');

-- ---- clean up the committed fixtures ----

select extensions.dblink_exec('setup', format($$delete from public.sync_mutations where owner_user_id = %L$$, owner_id))
from fixture;
select extensions.dblink_exec('setup', format($$delete from public.people where owner_user_id = %L$$, owner_id))
from fixture;
select extensions.dblink_exec('setup', format($$delete from auth.users where id = %L$$, owner_id)) from fixture;
select extensions.dblink_disconnect('setup');
select extensions.dblink_disconnect('first');
select extensions.dblink_disconnect('second');

select * from finish();
rollback;
