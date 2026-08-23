-- Fixora / TechFix — Supabase Storage setup for repair images (Block 4)
--
-- Manual step, same as schema.sql: run once in the Supabase SQL editor
-- (service_role context). The Android app only ever holds the anon key, so
-- every request from it arrives as `anon` — the policies below are what let
-- that anon key actually write and read repair images.
--
-- The bucket is public-read so an uploaded image's public URL can be stored
-- directly on the repair request (Firestore `imageUrls`) with no signed-URL
-- handling needed later in Repair Tracking / History. Anonymous uploads are
-- still gated by an explicit insert policy scoped to this one bucket.

insert into storage.buckets (id, name, public)
values ('repair-images', 'repair-images', true)
on conflict (id) do update set public = true;

create policy repair_images_anon_insert on storage.objects
    for insert to anon
    with check (bucket_id = 'repair-images');

create policy repair_images_public_read on storage.objects
    for select to anon, authenticated
    using (bucket_id = 'repair-images');
