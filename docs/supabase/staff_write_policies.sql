-- Fixora / TechFix — Supabase write policies for the staff screens (Block 7)
--
-- Run this in the Supabase SQL editor ONCE, on top of the existing schema.
-- It is separate from `schema.sql` on purpose: schema.sql drops and recreates
-- the tables, so re-running it would wipe the seeded data. This file only
-- adds policies and is safe to re-run on its own.
--
-- Secure fail-closed policy:
--   * spare_part_stock remains readable but is no longer writable from the
--     Android client. The app signs in with Firebase while Supabase sees the
--     shared anon role, so Postgres cannot distinguish staff roles.
--   * technicians       — NOT granted here. Technician CRUD is moving to
--     Firestore and is not authorized by this legacy Supabase policy file.
--   * spare_parts       — NOT granted. The parts catalogue itself is seed data.
--
-- Reintroduce writes only after an approved server-verifiable identity bridge
-- exists. Do not replace this with a client secret or another broad anon RPC.
-- Full Admin inventory management is now defined by:
--   supabase/migrations/20260823193000_admin_inventory_management.sql
--   supabase/functions/inventory-admin/
-- Those files keep this anonymous policy revoked and place the service-role
-- credential only inside a Firebase-role-verified Edge Function.

begin;

drop policy if exists spare_part_stock_insert on public.spare_part_stock;
drop policy if exists spare_part_stock_update on public.spare_part_stock;

commit;
