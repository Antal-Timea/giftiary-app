-- Wish photos: wishes name a photo only in their owner's folder, the owner keeps full control of
-- it, and a member of a shared list can read (only read) the photos of that list's live wishes and
-- nothing else of the owner's: no gift or person photo, no other list's wish, nothing once the
-- wish, the list or the membership is gone.
begin;
create extension if not exists pgtap with schema extensions;
select plan(33);

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
grant execute on function pg_temp.as_user(uuid) to authenticated, anon;

-- ---- the policy and its helper ----

select is(
    (select cmd from pg_policies where schemaname = 'storage' and tablename = 'objects' and policyname = 'wish_photos_select_shared'),
    'SELECT', 'members get a read policy only');
select is(
    (select array_to_string(roles, ',') from pg_policies where schemaname = 'storage' and policyname = 'wish_photos_select_shared'),
    'authenticated', 'for signed-in users only');
select ok(
    (select prosecdef from pg_proc where oid = 'public.can_read_shared_wish_photo(text)'::regprocedure),
    'the membership check runs as definer, since members cannot read wishes or memberships');
select ok(
    has_function_privilege('authenticated', 'public.can_read_shared_wish_photo(text)', 'execute')
        and not has_function_privilege('anon', 'public.can_read_shared_wish_photo(text)', 'execute')
        and not has_function_privilege('service_role', 'public.can_read_shared_wish_photo(text)', 'execute'),
    'only signed-in users can ask it');

-- ---- fixtures, written with full privileges ----

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'carol@example.test');

insert into public.people (last_mutation_id, id, owner_user_id, name, photo_path)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000005.jpg');
insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, name, given_at, photo_path)
values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-000000000001',
        'Book', '2026-05-12', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000004.jpg');

-- Alice's list S is shared with Carol; P is not shared.
insert into public.wishlists (last_mutation_id, id, owner_user_id, name) values
    (gen_random_uuid(), '10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Shared'),
    (gen_random_uuid(), '10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Private');
insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id)
values ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc');

-- ---- wish rows: a photo path only in the owner's folder ----

select lives_ok(
    $$insert into public.wishes (last_mutation_id, id, owner_user_id, wishlist_id, name, photo_path) values
        (gen_random_uuid(), '20000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001',
         'Lamp', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg'),
        (gen_random_uuid(), '20000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001',
         'Scarf', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000002.jpg'),
        (gen_random_uuid(), '20000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000002',
         'Pen', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000003.jpg'),
        (gen_random_uuid(), '20000000-0000-4000-8000-000000000004', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001',
         'Mug', null)$$,
    'a wish names a photo in its owner''s folder, or none');
select throws_ok(
    $$insert into public.wishes (last_mutation_id, id, owner_user_id, wishlist_id, name, photo_path)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001',
              'Lamp', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/40000000-0000-4000-8000-000000000001.jpg')$$,
    '23514', null, 'but not one in another account''s folder');
select throws_ok(
    $$insert into public.wishes (last_mutation_id, id, owner_user_id, wishlist_id, name, photo_path)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001',
              'Lamp', 'https://example.test/photo.jpg')$$,
    '23514', null, 'nor a URL or any other shape');

-- ---- objects: Alice's five photos and one of Bob's ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
insert into storage.objects (bucket_id, name) values
    ('gift-photos', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg'),
    ('gift-photos', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000002.jpg'),
    ('gift-photos', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000003.jpg'),
    ('gift-photos', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000004.jpg'),
    ('gift-photos', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000005.jpg');
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 5, 'Alice reads all her own photos');
reset role;

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
insert into storage.objects (bucket_id, name)
values ('gift-photos', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/40000000-0000-4000-8000-000000000001.jpg');
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 1, 'Bob, on none of her lists, reads only his own');
select is(public.can_read_shared_wish_photo('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg'), false,
    'and the helper says no to him');
reset role;

-- ---- a member reads the shared list's wish photos, and nothing else ----

select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
set local role authenticated;
select set_eq(
    $$select name from storage.objects where bucket_id = 'gift-photos'$$,
    array['aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg',
          'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000002.jpg'],
    'Carol reads exactly the photos of the shared list''s wishes');
select is(
    (select count(*)::int from storage.objects where name = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000003.jpg'),
    0, 'not a wish photo of a list not shared with her');
select is(
    (select count(*)::int from storage.objects where name = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000004.jpg'),
    0, 'not a gift photo');
select is(
    (select count(*)::int from storage.objects where name = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000005.jpg'),
    0, 'not a person photo');
select is(
    (select count(*)::int from storage.objects where name like 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/%'),
    0, 'nor anything of an account she shares nothing with');
with changed as (
    update storage.objects set metadata = '{"x": 1}'
    where name = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg' returning 1
)
select is((select count(*)::int from changed), 0, 'she cannot replace a photo she can read');
select throws_ok(
    $$insert into storage.objects (bucket_id, name) values ('gift-photos', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000009.jpg')$$,
    '42501', null, 'nor upload into the owner''s folder');
select is(
    (select jsonb_agg(w ->> 'photo_path' order by w ->> 'name')
     from jsonb_array_elements(public.get_shared_wishlist('10000000-0000-4000-8000-000000000001') -> 'wishes') w),
    '["aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg", null, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000002.jpg"]'::jsonb,
    'her view of the list names each wish''s photo, or none');
select is(
    (select count(*)::int
     from jsonb_array_elements(public.get_shared_wishlist('10000000-0000-4000-8000-000000000001') -> 'wishes') w,
          jsonb_object_keys(w) k
     where k not in ('wish_id', 'name', 'note', 'estimated_price_amount_minor', 'estimated_price_currency_code',
                     'product_url', 'photo_path', 'reservation_state')),
    0, 'and nothing else was added to a wish');
reset role;

-- A deleted wish's photo is no longer shown on the list, so it is no longer readable.
update public.wishes set deleted_at = now(), last_mutation_id = gen_random_uuid()
where id = '20000000-0000-4000-8000-000000000002';
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
set local role authenticated;
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 1, 'a deleted wish''s photo is not readable');
select is(
    (select count(*)::int from jsonb_array_elements(public.get_shared_wishlist('10000000-0000-4000-8000-000000000001') -> 'wishes')),
    2, 'nor is the wish listed');
reset role;

-- A replaced photo: the old object is no longer named, so it is no longer readable.
update public.wishes
set photo_path = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000003.jpg', last_mutation_id = gen_random_uuid()
where id = '20000000-0000-4000-8000-000000000001';
update public.wishes set photo_path = null, last_mutation_id = gen_random_uuid()
where id = '20000000-0000-4000-8000-000000000003';
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
set local role authenticated;
select set_eq(
    $$select name from storage.objects where bucket_id = 'gift-photos'$$,
    array['aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000003.jpg'],
    'after a replacement she reads the new photo, not the old one');
reset role;

-- A deleted list shows nothing.
update public.wishlists set deleted_at = now(), last_mutation_id = gen_random_uuid()
where id = '10000000-0000-4000-8000-000000000001';
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
set local role authenticated;
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 0, 'a deleted list''s photos are not readable');
reset role;
update public.wishlists set deleted_at = null, last_mutation_id = gen_random_uuid()
where id = '10000000-0000-4000-8000-000000000001';

-- Leaving (or being removed) ends the access.
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
set local role authenticated;
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 1, 'restored, the list''s photo is readable again');
select lives_ok($$select public.leave_wishlist('10000000-0000-4000-8000-000000000001')$$, 'Carol leaves the list');
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 0, 'and reads none of its photos any more');
select is(public.can_read_shared_wish_photo('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000003.jpg'), false,
    'the helper agrees');
reset role;

-- The owner is unaffected throughout.
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 5, 'Alice still reads all her photos');
select is(public.can_read_shared_wish_photo('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000003.jpg'), false,
    'she is no member of her own list; her own-folder policy is what lets her read');
reset role;

set local role anon;
select is((select count(*)::int from storage.objects where bucket_id = 'gift-photos'), 0, 'anon reads no photo');
select throws_ok($$select public.can_read_shared_wish_photo('x')$$, '42501', null, 'nor can it ask');
reset role;

-- ---- existing rows ----

select is(
    (select count(*)::int from public.wishes where photo_path is not null and photo_path !~ ('^' || owner_user_id::text || '/')),
    0, 'every wish photo is in its owner''s folder');
select has_index('public', 'wishes', 'wishes_photo_path_idx', 'members'' reads look wishes up by photo path');

select * from finish();
rollback;
