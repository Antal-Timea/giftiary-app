-- Gift photos: an optional photo per gift, stored as an object in a private Storage bucket.
--
-- The gift row keeps only the object's path (gifts.photo_path), never the image or a signed URL.
-- Paths are "<owner_user_id>/<photo id>.jpg": the first folder is the owner, which is what every
-- Storage policy below checks, so an account reaches only its own folder. Gifts are never shared,
-- so nobody else is ever given access; there is no public URL.
--
-- Objects are written and removed through the Storage API only (a trigger refuses direct deletes
-- from storage.objects). The app uploads a photo before the gift row that names it, and removes an
-- object only once no gift row names it any more; account deletion removes the account's folder
-- before calling public.delete_my_account().

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('gift-photos', 'gift-photos', false, 2097152, array['image/jpeg'])
on conflict (id) do nothing;

create policy gift_photos_select_own on storage.objects
    for select to authenticated
    using (bucket_id = 'gift-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy gift_photos_insert_own on storage.objects
    for insert to authenticated
    with check (bucket_id = 'gift-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Upsert of the same path (a retried upload) updates the existing object.
create policy gift_photos_update_own on storage.objects
    for update to authenticated
    using (bucket_id = 'gift-photos' and (storage.foldername(name))[1] = (select auth.uid())::text)
    with check (bucket_id = 'gift-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy gift_photos_delete_own on storage.objects
    for delete to authenticated
    using (bucket_id = 'gift-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- A gift may name only an object in its owner's own folder, in the one shape the app writes.
-- No gift sets a photo before this migration, so every existing row (all null) satisfies it.
alter table public.gifts
    add constraint gifts_photo_path_owned check (
        photo_path is null
        or photo_path ~ ('^' || owner_user_id::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$')
    );
