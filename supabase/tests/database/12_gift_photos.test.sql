-- Gift photos: a private bucket whose objects an account can read and write only in its own folder,
-- and gift rows that can name only an object in their owner's folder.
begin;
create extension if not exists pgtap with schema extensions;
select plan(20);

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
grant execute on function pg_temp.as_user(uuid) to authenticated, anon;

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test');

-- ---- the bucket ----

select is((select public from storage.buckets where id = 'gift-photos'), false, 'the gift photo bucket is private');
select is((select file_size_limit from storage.buckets where id = 'gift-photos'), 2097152::bigint, 'objects are at most 2 MiB');
select is((select allowed_mime_types from storage.buckets where id = 'gift-photos'), array['image/jpeg'], 'and JPEG only');
select is(
    (select count(*)::int from pg_policies where schemaname = 'storage' and tablename = 'objects' and policyname like 'gift_photos_%'),
    4, 'select, insert, update and delete each have a policy');
-- Direct deletes are refused by Storage's own trigger, so the delete policy is checked as written.
select ok(
    (select qual from pg_policies where schemaname = 'storage' and policyname = 'gift_photos_delete_own')
        like '%(storage.foldername(name))[1] = (( SELECT auth.uid() AS uid))::text%',
    'deleting is limited to the account''s own folder');
select is(
    (select string_agg(distinct array_to_string(roles, ','), ',') from pg_policies where schemaname = 'storage' and policyname like 'gift_photos_%'),
    'authenticated', 'the policies apply to signed-in users only');

-- ---- objects: only in the account's own folder ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select lives_ok(
    $$insert into storage.objects (bucket_id, name) values ('gift-photos', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/10000000-0000-4000-8000-000000000001.jpg')$$,
    'Alice uploads into her own folder');
select throws_ok(
    $$insert into storage.objects (bucket_id, name) values ('gift-photos', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/10000000-0000-4000-8000-000000000002.jpg')$$,
    '42501', null, 'but not into Bob''s');
select throws_ok(
    $$insert into storage.objects (bucket_id, name) values ('gift-photos', '10000000-0000-4000-8000-000000000003.jpg')$$,
    '42501', null, 'nor outside any folder');
reset role;

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select lives_ok(
    $$insert into storage.objects (bucket_id, name) values ('gift-photos', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/20000000-0000-4000-8000-000000000001.jpg')$$,
    'Bob uploads into his own folder');
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 1, 'Bob sees only his own photo');
select is(
    (select count(*)::int from storage.objects where name like 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/%'), 0,
    'and none of Alice''s');
with changed as (
    update storage.objects set metadata = '{"x": 1}'
    where name = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/10000000-0000-4000-8000-000000000001.jpg' returning 1
)
select is((select count(*)::int from changed), 0, 'Bob cannot overwrite Alice''s photo');
select throws_ok(
    $$update storage.objects set name = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/20000000-0000-4000-8000-000000000009.jpg'
      where name = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/20000000-0000-4000-8000-000000000001.jpg'$$,
    '42501', null, 'nor move his own photo into her folder');
reset role;

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 1, 'Alice sees only her own photo');
reset role;

set local role anon;
select throws_ok(
    $$insert into storage.objects (bucket_id, name) values ('gift-photos', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/10000000-0000-4000-8000-000000000004.jpg')$$,
    '42501', null, 'anon cannot upload');
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 0, 'nor read any photo');
reset role;

-- ---- gift rows: a photo path only in the owner's folder ----

insert into public.people (last_mutation_id, id, owner_user_id, name)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna');
select lives_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, name, given_at, photo_path)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-000000000001',
              'Book', '2026-05-12', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/10000000-0000-4000-8000-000000000001.jpg')$$,
    'a gift names a photo in its owner''s folder');
select throws_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, name, given_at, photo_path)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-000000000001',
              'Book', '2026-05-12', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/20000000-0000-4000-8000-000000000001.jpg')$$,
    '23514', null, 'but not one in another account''s folder');
select throws_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, name, given_at, photo_path)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-000000000001',
              'Book', '2026-05-12', 'https://example.test/photo.jpg')$$,
    '23514', null, 'nor a URL or any other shape');

select * from finish();
rollback;
