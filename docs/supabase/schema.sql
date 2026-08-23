-- Fixora customer application: spare-parts catalogue and branch stock.
--
-- The Android client reads these tables with a publishable/anon key. It has
-- no write policy. Branch records themselves remain in Firestore.

begin;

drop table if exists public.spare_part_stock;
drop table if exists public.spare_parts;

create table public.spare_parts (
    id                    uuid primary key default gen_random_uuid(),
    name                  text not null,
    category              text not null,
    compatible_categories text[] not null default '{}',
    is_available          boolean not null default true,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint spare_parts_compatible_check
        check (compatible_categories <@ array['MOBILE', 'LAPTOP', 'DESKTOP', 'TABLET']::text[])
);

create index spare_parts_compatible_categories_idx
    on public.spare_parts using gin (compatible_categories);

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

alter table public.spare_parts enable row level security;
alter table public.spare_part_stock enable row level security;

create policy spare_parts_read on public.spare_parts
    for select to anon, authenticated using (is_available = true);
create policy spare_part_stock_read on public.spare_part_stock
    for select to anon, authenticated using (true);

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

commit;
