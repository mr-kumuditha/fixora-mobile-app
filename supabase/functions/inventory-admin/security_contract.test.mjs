import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const migrationUrl = new URL("../../migrations/20260823193000_admin_inventory_management.sql", import.meta.url);

test("inventory migration removes anonymous stock writes", async () => {
  const sql = await readFile(migrationUrl, "utf8");
  assert.match(sql, /drop policy if exists spare_part_stock_insert/i);
  assert.match(sql, /drop policy if exists spare_part_stock_update/i);
  assert.doesNotMatch(sql, /create policy[\s\S]{0,120}for (insert|update|delete) to anon/i);
});

test("privileged inventory RPCs are unavailable to public roles", async () => {
  const sql = await readFile(migrationUrl, "utf8");
  for (const name of [
    "inventory_admin_create_item",
    "inventory_admin_update_item",
    "inventory_admin_set_item_availability",
    "inventory_admin_adjust_stock",
  ]) {
    assert.match(sql, new RegExp(`revoke all on function public\\.${name}[^;]+from public, anon, authenticated`, "i"));
    assert.match(sql, new RegExp(`grant execute on function public\\.${name}[^;]+to service_role`, "i"));
  }
});

