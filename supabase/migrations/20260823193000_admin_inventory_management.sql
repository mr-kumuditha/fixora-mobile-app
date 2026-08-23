-- Fixora Admin Inventory Management
--
-- This migration deliberately preserves the anonymous SELECT path required by
-- branch matching while keeping every mutation private to the service role.
-- Android never receives that credential. The inventory-admin Edge Function
-- verifies the Firebase user and ADMIN role before calling these RPCs.

begin;

alter table public.spare_parts
    add column if not exists is_available boolean not null default true,
    add column if not exists updated_at timestamptz not null default now(),
    add column if not exists archived_at timestamptz;

create table if not exists public.inventory_item_details (
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
    constraint inventory_item_details_selling_price_check check (selling_price is null or selling_price >= 0),
    constraint inventory_item_details_description_check check (description is null or char_length(description) <= 500),
    constraint inventory_item_details_supplier_name_check check (supplier_name is null or char_length(supplier_name) <= 120),
    constraint inventory_item_details_supplier_contact_check check (supplier_contact is null or char_length(supplier_contact) <= 160)
);

create unique index if not exists inventory_item_details_sku_unique_idx
    on public.inventory_item_details (lower(sku))
    where sku is not null;

-- Existing seeded items are valid inventory records. Their optional commercial
-- metadata remains unknown rather than being fabricated.
insert into public.inventory_item_details (
    part_id,
    minimum_stock_level,
    created_by_uid,
    updated_by_uid
)
select id, 0, 'system-migration', 'system-migration'
from public.spare_parts
on conflict (part_id) do nothing;

create table if not exists public.inventory_adjustments (
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

create index if not exists inventory_adjustments_part_created_idx
    on public.inventory_adjustments (part_id, created_at desc);
create index if not exists inventory_adjustments_created_idx
    on public.inventory_adjustments (created_at desc);

alter table public.inventory_item_details enable row level security;
alter table public.inventory_adjustments enable row level security;

-- Archived parts must disappear from the anonymous catalogue used by branch
-- matching. The service role used after ADMIN verification still sees them.
drop policy if exists spare_parts_read on public.spare_parts;
create policy spare_parts_read on public.spare_parts
    for select to anon, authenticated
    using (is_available = true);

-- Remove the legacy coursework policy if it is still live. No broad client
-- write policy replaces it.
drop policy if exists spare_part_stock_insert on public.spare_part_stock;
drop policy if exists spare_part_stock_update on public.spare_part_stock;

revoke all on public.inventory_item_details from anon, authenticated;
revoke all on public.inventory_adjustments from anon, authenticated;
grant all on public.inventory_item_details to service_role;
grant all on public.inventory_adjustments to service_role;

create or replace function public.inventory_admin_create_item(
    p_item_id uuid,
    p_name text,
    p_category text,
    p_compatible_categories text[],
    p_description text,
    p_sku text,
    p_minimum_stock_level integer,
    p_unit_cost numeric,
    p_selling_price numeric,
    p_supplier_name text,
    p_supplier_contact text,
    p_admin_uid text
) returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    -- The Android client creates the UUID once when the form opens. Retrying
    -- the same request therefore returns the same item instead of duplicating it.
    if exists (select 1 from public.spare_parts where id = p_item_id) then
        return p_item_id;
    end if;

    insert into public.spare_parts (
        id, name, category, compatible_categories, is_available, created_at, updated_at
    ) values (
        p_item_id, btrim(p_name), p_category, p_compatible_categories, true, now(), now()
    );

    insert into public.inventory_item_details (
        part_id, description, sku, minimum_stock_level, unit_cost, selling_price,
        supplier_name, supplier_contact, created_by_uid, updated_by_uid
    ) values (
        p_item_id, nullif(btrim(p_description), ''), nullif(btrim(p_sku), ''),
        p_minimum_stock_level, p_unit_cost, p_selling_price,
        nullif(btrim(p_supplier_name), ''), nullif(btrim(p_supplier_contact), ''),
        p_admin_uid, p_admin_uid
    );

    return p_item_id;
end;
$$;

create or replace function public.inventory_admin_update_item(
    p_item_id uuid,
    p_name text,
    p_category text,
    p_compatible_categories text[],
    p_description text,
    p_sku text,
    p_minimum_stock_level integer,
    p_unit_cost numeric,
    p_selling_price numeric,
    p_supplier_name text,
    p_supplier_contact text,
    p_is_available boolean,
    p_admin_uid text
) returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    update public.spare_parts
    set name = btrim(p_name),
        category = p_category,
        compatible_categories = p_compatible_categories,
        is_available = p_is_available,
        archived_at = case when p_is_available then null else coalesce(archived_at, now()) end,
        updated_at = now()
    where id = p_item_id;

    if not found then
        raise exception using errcode = 'P0002', message = 'inventory item not found';
    end if;

    insert into public.inventory_item_details (
        part_id, description, sku, minimum_stock_level, unit_cost, selling_price,
        supplier_name, supplier_contact, created_by_uid, updated_by_uid
    ) values (
        p_item_id, nullif(btrim(p_description), ''), nullif(btrim(p_sku), ''),
        p_minimum_stock_level, p_unit_cost, p_selling_price,
        nullif(btrim(p_supplier_name), ''), nullif(btrim(p_supplier_contact), ''),
        p_admin_uid, p_admin_uid
    )
    on conflict (part_id) do update set
        description = excluded.description,
        sku = excluded.sku,
        minimum_stock_level = excluded.minimum_stock_level,
        unit_cost = excluded.unit_cost,
        selling_price = excluded.selling_price,
        supplier_name = excluded.supplier_name,
        supplier_contact = excluded.supplier_contact,
        updated_by_uid = excluded.updated_by_uid,
        updated_at = now();

    return p_item_id;
end;
$$;

create or replace function public.inventory_admin_set_item_availability(
    p_item_id uuid,
    p_is_available boolean,
    p_admin_uid text
) returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    update public.spare_parts
    set is_available = p_is_available,
        archived_at = case when p_is_available then null else coalesce(archived_at, now()) end,
        updated_at = now()
    where id = p_item_id;

    if not found then
        raise exception using errcode = 'P0002', message = 'inventory item not found';
    end if;

    update public.inventory_item_details
    set updated_by_uid = p_admin_uid, updated_at = now()
    where part_id = p_item_id;

    return p_item_id;
end;
$$;

create or replace function public.inventory_admin_adjust_stock(
    p_request_id uuid,
    p_part_id uuid,
    p_branch_id text,
    p_adjustment_type text,
    p_quantity integer,
    p_reason text,
    p_admin_uid text,
    p_admin_email text
) returns table (
    adjustment_id uuid,
    previous_quantity integer,
    new_quantity integer,
    created_at timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_previous integer;
    v_new integer;
begin
    return query
    select a.id, a.previous_quantity, a.new_quantity, a.created_at
    from public.inventory_adjustments a
    where a.request_id = p_request_id;
    if found then
        return;
    end if;

    if p_branch_id not in ('colombo', 'galle') then
        raise exception using errcode = '22023', message = 'invalid branch';
    end if;
    if p_adjustment_type not in ('ADD', 'REMOVE', 'CORRECT') then
        raise exception using errcode = '22023', message = 'invalid adjustment type';
    end if;
    if char_length(btrim(p_reason)) not between 3 and 200 then
        raise exception using errcode = '22023', message = 'invalid adjustment reason';
    end if;
    if p_quantity < 0 or (p_adjustment_type in ('ADD', 'REMOVE') and p_quantity = 0) then
        raise exception using errcode = '22023', message = 'invalid adjustment quantity';
    end if;
    if not exists (
        select 1 from public.spare_parts
        where id = p_part_id and is_available = true
    ) then
        raise exception using errcode = 'P0002', message = 'active inventory item not found';
    end if;

    -- Serializes changes even when this branch has no stock row yet.
    perform pg_advisory_xact_lock(hashtextextended(p_part_id::text || ':' || p_branch_id, 0));

    select s.quantity into v_previous
    from public.spare_part_stock s
    where s.part_id = p_part_id and s.branch_id = p_branch_id
    for update;
    v_previous := coalesce(v_previous, 0);

    v_new := case p_adjustment_type
        when 'ADD' then v_previous + p_quantity
        when 'REMOVE' then v_previous - p_quantity
        else p_quantity
    end;

    if v_new < 0 or v_new > 999999999 then
        raise exception using errcode = '23514', message = 'stock quantity is outside the allowed range';
    end if;

    insert into public.spare_part_stock (part_id, branch_id, quantity, updated_at)
    values (p_part_id, p_branch_id, v_new, now())
    on conflict (part_id, branch_id) do update set
        quantity = excluded.quantity,
        updated_at = excluded.updated_at;

    return query
    insert into public.inventory_adjustments (
        request_id, part_id, branch_id, previous_quantity, new_quantity,
        adjustment_type, reason, performed_by_uid, performed_by_email
    ) values (
        p_request_id, p_part_id, p_branch_id, v_previous, v_new,
        p_adjustment_type, btrim(p_reason), p_admin_uid, nullif(btrim(p_admin_email), '')
    )
    returning id, inventory_adjustments.previous_quantity,
        inventory_adjustments.new_quantity, inventory_adjustments.created_at;
end;
$$;

revoke all on function public.inventory_admin_create_item(uuid, text, text, text[], text, text, integer, numeric, numeric, text, text, text) from public, anon, authenticated;
revoke all on function public.inventory_admin_update_item(uuid, text, text, text[], text, text, integer, numeric, numeric, text, text, boolean, text) from public, anon, authenticated;
revoke all on function public.inventory_admin_set_item_availability(uuid, boolean, text) from public, anon, authenticated;
revoke all on function public.inventory_admin_adjust_stock(uuid, uuid, text, text, integer, text, text, text) from public, anon, authenticated;

grant execute on function public.inventory_admin_create_item(uuid, text, text, text[], text, text, integer, numeric, numeric, text, text, text) to service_role;
grant execute on function public.inventory_admin_update_item(uuid, text, text, text[], text, text, integer, numeric, numeric, text, text, boolean, text) to service_role;
grant execute on function public.inventory_admin_set_item_availability(uuid, boolean, text) to service_role;
grant execute on function public.inventory_admin_adjust_stock(uuid, uuid, text, text, integer, text, text, text) to service_role;

commit;
