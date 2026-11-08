-- Person and profile photos, schema only: who may be named where, and a private bucket for
-- profile avatars. Nothing here shares a photo with anyone; both kinds stay with their owner.
--
-- A person's photo is an object in the existing private gift-photos bucket, in the same flat
-- "<owner_user_id>/<photo id>.jpg" shape as a gift's: its owner-only policies and account
-- deletion (which empties the owner's folder) already cover it, so that bucket is unchanged.
--
-- A profile avatar lives in its own private profile-avatars bucket, so that if it is ever shown to
-- other accounts, that access can never reach a gift or person photo.

-- Both columns have existed since the initial schema, but no client has ever set them, so every
-- existing row is null and satisfies the checks. If one did not, this migration fails rather than
-- rewriting it.
alter table public.people
    add constraint people_photo_path_owned check (
        photo_path is null
        or photo_path ~ ('^' || owner_user_id::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$')
    );

alter table public.profiles
    add constraint profiles_avatar_path_owned check (
        avatar_path is null
        or avatar_path ~ ('^' || id::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$')
    );

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('profile-avatars', 'profile-avatars', false, 524288, array['image/jpeg'])
on conflict (id) do nothing;

-- Owner-only, and only the one flat shape the app writes: no subfolders, which deleting an
-- account's folder (a one-level listing) would miss.
create policy profile_avatars_select_own on storage.objects
    for select to authenticated
    using (bucket_id = 'profile-avatars' and name ~ ('^' || (select auth.uid())::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$'));

create policy profile_avatars_insert_own on storage.objects
    for insert to authenticated
    with check (bucket_id = 'profile-avatars' and name ~ ('^' || (select auth.uid())::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$'));

-- Upsert of the same path (a retried upload) updates the existing object.
create policy profile_avatars_update_own on storage.objects
    for update to authenticated
    using (bucket_id = 'profile-avatars' and name ~ ('^' || (select auth.uid())::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$'))
    with check (bucket_id = 'profile-avatars' and name ~ ('^' || (select auth.uid())::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$'));

create policy profile_avatars_delete_own on storage.objects
    for delete to authenticated
    using (bucket_id = 'profile-avatars' and name ~ ('^' || (select auth.uid())::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$'));
