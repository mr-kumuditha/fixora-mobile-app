-- Fixora / TechFix — Supabase Postgres schema and seed data (Block 3)
--
-- Supabase holds exactly two things (see CLAUDE.md): this relational slice
-- (technicians, technician-branch assignment, spare parts, spare-part stock)
-- and Storage for repair images. Everything else is Firestore.
--
-- branch_id is a text column holding the Firestore `branches` document id
-- ('colombo' / 'galle'). It is deliberately not a foreign key: the branch
-- records themselves live in Firestore, so Postgres cannot enforce it.
--
-- Safe to re-run: it drops and recreates the three tables and their seed rows.

begin;

drop table if exists public.inventory_adjustments;
drop table if exists public.inventory_item_details;
drop table if exists public.spare_part_stock;
drop table if exists public.spare_parts;
drop table if exists public.technicians;

-- ---------------------------------------------------------------- technicians

create table public.technicians (
    id              uuid primary key default gen_random_uuid(),
    name            text        not null,
    branch_id       text        not null,
    category_skills text[]      not null default '{}',
    available       boolean     not null default true,
    created_at      timestamptz not null default now(),
    constraint technicians_branch_id_check check (branch_id in ('colombo', 'galle')),
    constraint technicians_skills_check
        check (category_skills <@ array['MOBILE', 'LAPTOP', 'DESKTOP', 'TABLET']::text[])
);

-- The branch-matching query filters on branch + availability, then on skill.
create index technicians_branch_available_idx
    on public.technicians (branch_id, available);
create index technicians_category_skills_idx
    on public.technicians using gin (category_skills);

-- ---------------------------------------------------------------- spare_parts

create table public.spare_parts (
    id                    uuid primary key default gen_random_uuid(),
    name                  text   not null,
    -- Part type: SCREEN, BATTERY, PORT, KEYBOARD, STORAGE, POWER, COOLING.
    category              text   not null,
    -- Which device categories this part fits.
    compatible_categories text[] not null default '{}',
    is_available          boolean not null default true,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    archived_at           timestamptz,
    constraint spare_parts_compatible_check
        check (compatible_categories <@ array['MOBILE', 'LAPTOP', 'DESKTOP', 'TABLET']::text[])
);

create index spare_parts_compatible_categories_idx
    on public.spare_parts using gin (compatible_categories);

-- ----------------------------------------------------------- spare_part_stock

create table public.spare_part_stock (
    id         uuid primary key default gen_random_uuid(),
    part_id    uuid not null references public.spare_parts (id) on delete cascade,
    branch_id  text not null,
    quantity   integer not null default 0,
    updated_at timestamptz not null default now(),
    constraint spare_part_stock_branch_id_check check (branch_id in ('colombo', 'galle')),
    constraint spare_part_stock_quantity_check check (quantity >= 0),
    constraint spare_part_stock_unique_part_branch unique (part_id, branch_id)
);

create index spare_part_stock_branch_idx on public.spare_part_stock (branch_id);

-- ---------------------------------------------------- admin inventory details
-- Commercial and supplier fields are isolated from the anonymously readable
-- spare-parts catalogue. Only the Firebase-role-verified Edge Function reads
-- or mutates this table with the Supabase service role.

create table public.inventory_item_details (
    part_id              uuid primary key references public.spare_parts (id) on delete cascade,
    description          text,
    sku                  text,
    minimum_stock_level  integer not null default 0,
    unit_cost            numeric(12, 2),
    selling_price        numeric(12, 2),
    supplier_name        text,
    supplier_contact     text,
    created_by_uid       text not null,
    updated_by_uid       text not null,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    constraint inventory_item_details_minimum_check check (minimum_stock_level >= 0),
    constraint inventory_item_details_unit_cost_check check (unit_cost is null or unit_cost >= 0),
    constraint inventory_item_details_selling_price_check check (selling_price is null or selling_price >= 0)
);
create unique index inventory_item_details_sku_unique_idx
    on public.inventory_item_details (lower(sku)) where sku is not null;

create table public.inventory_adjustments (
    id                       uuid primary key default gen_random_uuid(),
    request_id               uuid not null unique,
    part_id                  uuid not null references public.spare_parts (id) on delete restrict,
    branch_id                text not null,
    previous_quantity        integer not null,
    new_quantity             integer not null,
    adjustment_type          text not null,
    reason                   text not null,
    performed_by_uid         text not null,
    performed_by_email       text,
    created_at               timestamptz not null default now(),
    constraint inventory_adjustments_branch_check check (branch_id in ('colombo', 'galle')),
    constraint inventory_adjustments_previous_check check (previous_quantity >= 0),
    constraint inventory_adjustments_new_check check (new_quantity >= 0),
    constraint inventory_adjustments_type_check check (adjustment_type in ('ADD', 'REMOVE', 'CORRECT')),
    constraint inventory_adjustments_reason_check check (char_length(btrim(reason)) between 3 and 200)
);
create index inventory_adjustments_part_created_idx on public.inventory_adjustments (part_id, created_at desc);
create index inventory_adjustments_created_idx on public.inventory_adjustments (created_at desc);

-- ------------------------------------------------------------------------ RLS
--
-- The Android app authenticates against Firebase, not Supabase, so every
-- request from the app arrives as the `anon` role carrying the publishable
-- anon key. Read-only for now: nothing in the customer flow writes to these
-- tables. Block 7's staff screens will need explicit update policies for
-- technicians.available and spare_part_stock.quantity — deliberately not
-- granted here, so no anon write path exists before it is actually needed.

alter table public.technicians      enable row level security;
alter table public.spare_parts      enable row level security;
alter table public.spare_part_stock enable row level security;
alter table public.inventory_item_details enable row level security;
alter table public.inventory_adjustments enable row level security;

create policy technicians_read      on public.technicians
    for select to anon, authenticated using (true);
create policy spare_parts_read      on public.spare_parts
    for select to anon, authenticated using (is_available = true);
create policy spare_part_stock_read on public.spare_part_stock
    for select to anon, authenticated using (true);

-- ------------------------------------------------------------------ seed data
--
-- Deliberate gaps, so Block 5's matching has something real to decide on:
--   * Galle has no DESKTOP technician at all.
--   * Colombo's third technician is marked unavailable.
--   * Part stock is uneven, with several parts at zero at one branch.

insert into public.technicians (name, branch_id, category_skills, available) values
    ('Nuwan Perera',       'colombo', array['MOBILE', 'TABLET'],  true),
    ('Dilshan Fernando',   'colombo', array['LAPTOP', 'DESKTOP'], true),
    ('Ishara Silva',       'colombo', array['MOBILE', 'LAPTOP'],  false),
    ('Kasun Jayawardena',  'galle',   array['MOBILE', 'TABLET'],  true),
    ('Tharindu Bandara',   'galle',   array['LAPTOP'],            true),
    ('Sanduni Rathnayake', 'galle',   array['MOBILE'],            false);

insert into public.spare_parts (name, category, compatible_categories) values
    ('Mobile Display Panel',      'SCREEN',   array['MOBILE']),
    ('Mobile Battery Pack',       'BATTERY',  array['MOBILE']),
    ('USB-C Charging Port Flex',  'PORT',     array['MOBILE', 'TABLET']),
    ('Laptop LCD Panel 15.6"',    'SCREEN',   array['LAPTOP']),
    ('Laptop Keyboard Module',    'KEYBOARD', array['LAPTOP']),
    ('512GB NVMe SSD',            'STORAGE',  array['LAPTOP', 'DESKTOP']),
    ('ATX Power Supply 600W',     'POWER',    array['DESKTOP']),
    ('Tablet Display Assembly',   'SCREEN',   array['TABLET']),
    ('Thermal Paste Kit',         'COOLING',  array['LAPTOP', 'DESKTOP']);

insert into public.spare_part_stock (part_id, branch_id, quantity)
select p.id, s.branch_id, s.quantity
from (values
    ('Mobile Display Panel',     'colombo', 12),
    ('Mobile Display Panel',     'galle',    3),
    ('Mobile Battery Pack',      'colombo',  8),
    ('Mobile Battery Pack',      'galle',    0),
    ('USB-C Charging Port Flex', 'colombo',  5),
    ('USB-C Charging Port Flex', 'galle',    6),
    ('Laptop LCD Panel 15.6"',   'colombo',  4),
    ('Laptop LCD Panel 15.6"',   'galle',    0),
    ('Laptop Keyboard Module',   'colombo',  0),
    ('Laptop Keyboard Module',   'galle',    7),
    ('512GB NVMe SSD',           'colombo', 10),
    ('512GB NVMe SSD',           'galle',    2),
    ('ATX Power Supply 600W',    'colombo',  6),
    ('ATX Power Supply 600W',    'galle',    0),
    ('Tablet Display Assembly',  'colombo',  2),
    ('Tablet Display Assembly',  'galle',    5),
    ('Thermal Paste Kit',        'colombo', 20),
    ('Thermal Paste Kit',        'galle',   15)
) as s (part_name, branch_id, quantity)
join public.spare_parts p on p.name = s.part_name;

insert into public.inventory_item_details (
    part_id, minimum_stock_level, created_by_uid, updated_by_uid
)
select id, 0, 'system-seed', 'system-seed'
from public.spare_parts;

commit;
