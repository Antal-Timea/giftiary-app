-- Gift idea photos: an idea may name one photo, kept like gift, person and wish photos in the
-- private gift-photos bucket at "<owner>/<photo id>.jpg". Only the owner reads and writes it,
-- through the existing own-folder policies; no policy or function changes. When an idea is marked
-- as given, the app moves the photo to the gift, so the archived idea names none.

-- The app has never written an idea photo, so every existing row has none and the check holds.
alter table public.gift_ideas
    add constraint gift_ideas_photo_path_owned check (
        photo_path is null
        or photo_path ~ ('^' || owner_user_id::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$')
    );
