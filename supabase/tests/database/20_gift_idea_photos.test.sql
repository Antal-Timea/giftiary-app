-- Gift idea photos: an idea names a photo only in its owner's folder, and nobody else can read it.
begin;
create extension if not exists pgtap with schema extensions;
select plan(7);

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
grant execute on function pg_temp.as_user(uuid) to authenticated, anon;

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test');
insert into public.people (last_mutation_id, id, owner_user_id, name)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna');

-- ---- idea rows: a photo path only in the owner's folder ----

select lives_ok(
    $$insert into public.gift_ideas (last_mutation_id, id, owner_user_id, recipient_person_id, name, status, photo_path) values
        (gen_random_uuid(), '20000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-000000000001',
         'Scarf', 'IDEA', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg'),
        (gen_random_uuid(), '20000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-000000000001',
         'Mug', 'PLANNED', null)$$,
    'an idea names a photo in its owner''s folder, or none');
select throws_ok(
    $$insert into public.gift_ideas (last_mutation_id, id, owner_user_id, recipient_person_id, name, status, photo_path)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-000000000001',
              'Scarf', 'IDEA', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/40000000-0000-4000-8000-000000000001.jpg')$$,
    '23514', null, 'but not one in another account''s folder');
select throws_ok(
    $$update public.gift_ideas set photo_path = 'https://example.test/photo.jpg', last_mutation_id = gen_random_uuid()
      where id = '20000000-0000-4000-8000-000000000002'$$,
    '23514', null, 'nor a URL or any other shape, on update either');

-- ---- objects: the owner's own folder only ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
insert into storage.objects (bucket_id, name)
values ('gift-photos', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg');
select is(
    (select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 1, 'the owner reads her idea''s photo');
reset role;

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 0, 'another account reads no idea photo');
select is((select count(*)::int from public.gift_ideas), 0, 'nor the idea that names it');
reset role;

-- ---- existing rows ----

select is(
    (select count(*)::int from public.gift_ideas where photo_path is not null and photo_path !~ ('^' || owner_user_id::text || '/')),
    0, 'every idea photo is in its owner''s folder');

select * from finish();
rollback;
