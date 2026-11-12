-- A device proof answers one question, for the device that holds its secret only: does the account
-- that registered it still exist? It names no account, so nobody can probe for anyone else's, and it
-- goes with the account, so nothing about a deleted account stays behind.

begin;
create extension if not exists pgtap with schema extensions;
select plan(38);

-- ---- fixtures ----

insert into auth.users (id, email, email_confirmed_at, raw_user_meta_data) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test', now(), '{"display_name": "Alice"}'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test', now(), '{"display_name": "Bob"}');

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
create function pg_temp.hash_of(secret text) returns text language sql immutable as $$
    select encode(sha256(decode(secret, 'hex')), 'hex')
$$;
-- Counted as the table owner, past every grant.
create function pg_temp.proofs_of(id uuid) returns int language sql security definer as $$
    select count(*)::int from private.account_device_proofs where user_id = id
$$;
grant execute on function pg_temp.as_user(uuid), pg_temp.hash_of(text), pg_temp.proofs_of(uuid) to anon, authenticated;

-- Alice's phone and tablet, Bob's phone. Secrets are what each device keeps; the server sees hashes.
select set_config('test.alice_phone', repeat('a1', 32), true);
select set_config('test.alice_tablet', repeat('a2', 32), true);
select set_config('test.bob_phone', repeat('b1', 32), true);

-- ---- what clients can reach ----

select hasnt_table('public', 'account_device_proofs', 'the proofs are not in the API schema');
select table_privs_are('private', 'account_device_proofs', 'anon', array[]::text[], 'anon has no access to the proofs table');
select table_privs_are('private', 'account_device_proofs', 'authenticated', array[]::text[], 'nor has authenticated');
select ok(not has_function_privilege('anon', 'public.register_device_proof(text)', 'execute'), 'anon cannot register');
select ok(has_function_privilege('authenticated', 'public.register_device_proof(text)', 'execute'), 'a signed-in account can');
select ok(has_function_privilege('anon', 'public.device_proof_is_live(text)', 'execute')
          and has_function_privilege('authenticated', 'public.device_proof_is_live(text)', 'execute'),
    'the question needs no session');
select ok(not has_function_privilege('authenticated', 'private.account_device_proofs_max()', 'execute')
          and not has_function_privilege('anon', 'private.account_device_proofs_max()', 'execute'),
    'the limit helper is not callable by clients');
select is((select array_agg(proconfig[1] order by proname::text) from pg_proc
           where oid in ('public.register_device_proof(text)'::regprocedure, 'public.device_proof_is_live(text)'::regprocedure)),
    array['search_path=""', 'search_path=""'], 'both functions run with an empty search_path');
select is((select pg_get_function_identity_arguments('public.device_proof_is_live(text)'::regprocedure)), 'p_proof text',
    'the question takes the secret alone, no account id');
select is((select prorettype::regtype::text from pg_proc where oid = 'public.device_proof_is_live(text)'::regprocedure), 'boolean',
    'and answers one boolean, nothing about the account');

-- ---- registering ----

set local role anon;
select throws_ok(format('select public.register_device_proof(%L)', pg_temp.hash_of(current_setting('test.alice_phone'))),
    '42501', null, 'without a session nothing is registered');
reset role;
select set_config('request.jwt.claims', '{"role": "authenticated"}', true);
set local role authenticated;
select throws_ok(format('select public.register_device_proof(%L)', pg_temp.hash_of(current_setting('test.alice_phone'))),
    '42501', 'not authenticated', 'nor for a request without a user');
reset role;

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select lives_ok(format('select public.register_device_proof(%L)', pg_temp.hash_of(current_setting('test.alice_phone'))),
    'Alice''s phone registers its proof');
select lives_ok(format('select public.register_device_proof(%L)', pg_temp.hash_of(current_setting('test.alice_phone'))),
    'registering it again after a lost answer succeeds');
select lives_ok(format('select public.register_device_proof(%L)', pg_temp.hash_of(current_setting('test.alice_tablet'))),
    'so does her tablet');
select throws_ok($$select public.register_device_proof('ABCD')$$, '22023', null, 'a malformed proof is refused');
select throws_ok(format('select public.register_device_proof(%L)', upper(pg_temp.hash_of(current_setting('test.alice_phone')))),
    '22023', null, 'only lower-case hex is a proof');
select throws_ok($$select public.register_device_proof(null)$$, '22023', null, 'and null is not one');
reset role;
select is(pg_temp.proofs_of('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 2, 'Alice has one proof per device');

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select throws_ok(format('select public.register_device_proof(%L)', pg_temp.hash_of(current_setting('test.alice_phone'))),
    '22023', null, 'Bob cannot take over a proof of Alice''s');
select lives_ok(format('select public.register_device_proof(%L)', pg_temp.hash_of(current_setting('test.bob_phone'))),
    'Bob registers his own');
reset role;
select is(pg_temp.proofs_of('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 2, 'Alice''s proofs are still hers');

-- ---- asking ----

set local role anon;
select is(public.device_proof_is_live(current_setting('test.alice_phone')), true, 'Alice''s phone learns her account exists');
select is(public.device_proof_is_live(current_setting('test.bob_phone')), true, 'and Bob''s phone that his does');
select is(public.device_proof_is_live(repeat('c3', 32)), false, 'a secret nobody registered says nothing about any account');
select is(public.device_proof_is_live(pg_temp.hash_of(current_setting('test.alice_phone'))), false,
    'the stored hash is not the secret, so a leaked hash proves nothing');
select throws_ok($$select public.device_proof_is_live('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')$$, '22023', null,
    'an account id is not a question it answers');
select throws_ok($$select public.device_proof_is_live(null)$$, '22023', null, 'malformed input is an error, never false');
select throws_ok(format('select public.device_proof_is_live(%L)', upper(current_setting('test.alice_phone'))), '22023', null,
    'not even with upper-case hex');
select throws_ok($$select * from private.account_device_proofs$$, '42501', null, 'and the table cannot be read around it');
reset role;

-- ---- the limit ----

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select lives_ok($$select public.register_device_proof(encode(sha256(convert_to('bob-' || n, 'UTF8')), 'hex'))
                  from generate_series(2, 20) n$$, 'Bob registers up to the limit');
select throws_ok($$select public.register_device_proof(encode(sha256('bob-21'::bytea), 'hex'))$$, '54000', null,
    'one more is refused, and that device simply has no proof');
reset role;
select is(pg_temp.proofs_of('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'), 20, 'Bob keeps exactly the limit');

-- ---- Alice deletes her account ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select lives_ok($$select public.delete_my_account()$$, 'Alice deletes her account');
reset role;

select is(pg_temp.proofs_of('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her proofs went with it: nothing of her stays');
set local role anon;
select is(public.device_proof_is_live(current_setting('test.alice_tablet')), false,
    'so her tablet, which still holds its secret, learns the account is gone');
select is(public.device_proof_is_live(current_setting('test.bob_phone')), true, 'while Bob''s account is untouched');
reset role;

-- A token of hers that is still valid cannot register a proof for the deleted account.
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select throws_ok(format('select public.register_device_proof(%L)', pg_temp.hash_of(repeat('a4', 32))),
    '23503', null, 'a stale token cannot bring a proof back');
reset role;

select * from finish();
rollback;
