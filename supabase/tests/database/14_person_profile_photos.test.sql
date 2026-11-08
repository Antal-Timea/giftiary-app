-- Person and profile photos: a person may name only a photo in its owner's folder, a profile only
-- an avatar in its own folder, and the private profile-avatars bucket lets an account reach only
-- its own avatars. Nothing about gift photos, people or sharing becomes visible to anyone else.
begin;
create extension if not exists pgtap with schema extensions;
select plan(28);

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
grant execute on function pg_temp.as_user(uuid) to authenticated, anon;

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test');

-- ---- the avatar bucket ----

select is((select public from storage.buckets where id = 'profile-avatars'), false, 'the avatar bucket is private');
select is((select file_size_limit from storage.buckets where id = 'profile-avatars'), 524288::bigint, 'avatars are at most 512 KiB');
select is((select allowed_mime_types from storage.buckets where id = 'profile-avatars'), array['image/jpeg'], 'and JPEG only');
select is(
    (select count(*)::int from pg_policies where schemaname = 'storage' and tablename = 'objects' and policyname like 'profile_avatars_%'),
    4, 'select, insert, update and delete each have a policy');
select is(
    (select string_agg(distinct array_to_string(roles, ','), ',') from pg_policies where schemaname = 'storage' and policyname like 'profile_avatars_%'),
    'authenticated', 'the policies apply to signed-in users only');
-- Direct deletes are refused by Storage's own trigger, so the delete policy is checked as written.
select ok(
    (select qual from pg_policies where schemaname = 'storage' and policyname = 'profile_avatars_delete_own')
        like '%( SELECT auth.uid() AS uid)%',
    'deleting is limited to the account''s own avatars');
select is(
    (select count(*)::int from pg_policies where schemaname = 'storage' and tablename = 'objects'
        and (policyname like 'gift_photos_%' or qual like '%gift-photos%' or with_check like '%gift-photos%')),
    4, 'the gift photo bucket keeps exactly its four owner-only policies');

-- ---- avatar objects: only the account's own, in the one flat shape ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select lives_ok(
    $$insert into storage.objects (bucket_id, name) values ('profile-avatars', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg')$$,
    'Alice uploads an avatar into her own folder');
select throws_ok(
    $$insert into storage.objects (bucket_id, name) values ('profile-avatars', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/30000000-0000-4000-8000-000000000002.jpg')$$,
    '42501', null, 'but not into Bob''s');
select throws_ok(
    $$insert into storage.objects (bucket_id, name) values ('profile-avatars', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/old/30000000-0000-4000-8000-000000000003.jpg')$$,
    '42501', null, 'nor into a subfolder of her own');
select throws_ok(
    $$insert into storage.objects (bucket_id, name) values ('profile-avatars', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/me.png')$$,
    '42501', null, 'nor under any other name');
reset role;

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select lives_ok(
    $$insert into storage.objects (bucket_id, name) values ('profile-avatars', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/40000000-0000-4000-8000-000000000001.jpg')$$,
    'Bob uploads his own avatar');
select is((select count(*)::int from storage.objects where bucket_id = 'profile-avatars'), 1, 'Bob sees only his own avatar, none of Alice''s');
with changed as (
    update storage.objects set metadata = '{"x": 1}'
    where name = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg' returning 1
)
select is((select count(*)::int from changed), 0, 'Bob cannot overwrite Alice''s avatar');
select throws_ok(
    $$update storage.objects set name = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/40000000-0000-4000-8000-000000000009.jpg'
      where name = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/40000000-0000-4000-8000-000000000001.jpg'$$,
    '42501', null, 'nor move his own avatar into her folder');
reset role;

set local role anon;
select throws_ok(
    $$insert into storage.objects (bucket_id, name) values ('profile-avatars', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000004.jpg')$$,
    '42501', null, 'anon cannot upload an avatar');
select is((select count(*)::int from storage.objects where bucket_id = 'profile-avatars'), 0, 'nor read any');
reset role;

-- ---- people: a photo path only in the owner's folder ----

select lives_ok(
    $$insert into public.people (last_mutation_id, id, owner_user_id, name, photo_path)
      values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna',
              'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/10000000-0000-4000-8000-000000000001.jpg')$$,
    'a person names a photo in its owner''s folder');
select throws_ok(
    $$insert into public.people (last_mutation_id, id, owner_user_id, name, photo_path)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna',
              'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/20000000-0000-4000-8000-000000000001.jpg')$$,
    '23514', null, 'but not one in another account''s folder');
select throws_ok(
    $$insert into public.people (last_mutation_id, id, owner_user_id, name, photo_path)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna', 'https://example.test/anna.jpg')$$,
    '23514', null, 'nor a URL');
select throws_ok(
    $$insert into public.people (last_mutation_id, id, owner_user_id, name, photo_path)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna',
              'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/people/10000000-0000-4000-8000-000000000002.jpg')$$,
    '23514', null, 'nor a subfolder of the owner''s own');

-- ---- profiles: an avatar path only in its own folder ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select lives_ok(
    $$update public.profiles set avatar_path = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg'
      where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$$,
    'Alice sets her own avatar');
select throws_ok(
    $$update public.profiles set avatar_path = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/40000000-0000-4000-8000-000000000001.jpg'
      where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$$,
    '23514', null, 'but cannot name Bob''s');
select throws_ok(
    $$update public.profiles set avatar_path = 'https://example.test/alice.jpg' where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$$,
    '23514', null, 'nor a URL');
with changed as (
    update public.profiles set avatar_path = null where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' returning 1
)
select is((select count(*)::int from changed), 0, 'nor change Bob''s profile');
reset role;

-- ---- nothing becomes visible to anyone else ----

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select is((select count(*)::int from public.people where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0,
    'Bob cannot read Alice''s person or its photo path');
select is((select count(*)::int from public.profiles where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0,
    'nor her profile or its avatar path');
reset role;

-- The sharing and invite functions return names only: no function hands out a photo or avatar path.
select is(
    (select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
        where n.nspname in ('public', 'private') and p.prosrc ~ '(photo_path|avatar_path)'),
    0, 'no database function reads a photo or avatar path');

select * from finish();
rollback;
