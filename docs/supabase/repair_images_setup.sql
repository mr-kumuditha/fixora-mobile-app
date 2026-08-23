-- Fixora customer-image Storage setup.
--
-- Run once in the SQL editor for the SAME Supabase project referenced by
-- Android's SUPABASE_URL. The Android app authenticates users with Firebase
-- and currently sends only the Supabase publishable/anon key to Storage.
-- Consequently uploads execute as `anon`; Firebase uid is used only as the
-- first object-path segment and is not a Supabase-authenticated identity.
-- Repair photos use <firebaseUid>/<uuid>.jpg. Profile photos reuse this live
-- bucket as <firebaseUid>/profile_<uuid>.jpg, keeping the existing one-folder
-- policy and JPEG validation. The active profile URL is owner-writable only
-- through Firestore users/{uid}; Storage objects stay immutable.

begin;

insert into storage.buckets (
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
)
values (
    'repair-images',
    'repair-images',
    true,
    5242880,
    array['image/jpeg']::text[]
)
on conflict (id) do update
set name = excluded.name,
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "fixora_repair_images_insert" on storage.objects;

create policy "fixora_repair_images_insert"
on storage.objects
for insert
to anon, authenticated
with check (
    bucket_id = 'repair-images'
    and (storage.foldername(name))[1] is not null
    and (storage.foldername(name))[2] is null
    and char_length((storage.foldername(name))[1]) between 1 and 128
    and lower(storage.extension(name)) in ('jpg', 'jpeg')
);

commit;

-- This bucket is public because Firestore repair requests and user profiles
-- store public URLs.
-- Public object retrieval does not need a SELECT policy. Listing, UPDATE, and
-- DELETE remain denied. In particular, granting anonymous DELETE would let
-- anyone who learns an object URL delete a customer's repair evidence.
-- Removing a profile photo therefore clears its Firestore pointer; it does
-- not grant the anonymous Android client permission to delete Storage data.

-- Verification queries for the SQL editor:
select id, name, public, file_size_limit, allowed_mime_types
from storage.buckets
where id = 'repair-images';

select policyname, roles, cmd, qual, with_check
from pg_policies
where schemaname = 'storage'
  and tablename = 'objects'
  and policyname = 'fixora_repair_images_insert';
