import { createClient, SupabaseClient } from "npm:@supabase/supabase-js@2";
import { verifyFirebaseAdmin } from "./auth.mjs";

const FIREBASE_PROJECT_ID = Deno.env.get("FIREBASE_PROJECT_ID") ?? "techfix-mobile-app";
const allowedDeviceCategories = new Set(["MOBILE", "LAPTOP", "DESKTOP", "TABLET"]);
const allowedAdjustmentTypes = new Set(["ADD", "REMOVE", "CORRECT"]);

type JsonRecord = Record<string, unknown>;

const json = (body: JsonRecord, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8" },
});

function serviceRoleKey(): string {
  const legacy = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (legacy) return legacy;
  const raw = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (!raw) throw new Error("Supabase secret key is unavailable");
  const keys = JSON.parse(raw) as Record<string, string>;
  const key = keys.default ?? Object.values(keys)[0];
  if (!key) throw new Error("Supabase secret key is unavailable");
  return key;
}

function requiredString(value: unknown, name: string, min: number, max: number): string {
  if (typeof value !== "string") throw new Error(`${name} is required`);
  const trimmed = value.trim();
  if (trimmed.length < min || trimmed.length > max) throw new Error(`${name} is invalid`);
  return trimmed;
}

function optionalString(value: unknown, max: number): string {
  if (value == null) return "";
  if (typeof value !== "string") throw new Error("Invalid text value");
  const trimmed = value.trim();
  if (trimmed.length > max) throw new Error("Text value is too long");
  return trimmed;
}

function nonNegativeInteger(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isInteger(value) || value < 0 || value > 999999999) {
    throw new Error(`${name} is invalid`);
  }
  return value;
}

function optionalMoney(value: unknown): number | null {
  if (value == null) return null;
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0 || value > 999999999.99) {
    throw new Error("Invalid monetary value");
  }
  return Math.round(value * 100) / 100;
}

function uuid(value: unknown, name = "request id"): string {
  if (typeof value !== "string" || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
    throw new Error(`${name} is invalid`);
  }
  return value;
}

function itemParameters(payload: JsonRecord) {
  const category = requiredString(payload.category, "Category", 2, 40)
    .toUpperCase().replace(/[\s-]+/g, "_");
  if (!/^[A-Z0-9_]+$/.test(category)) throw new Error("Category is invalid");

  if (!Array.isArray(payload.compatibleCategories)) throw new Error("Compatible categories are required");
  const compatibleCategories = [...new Set(payload.compatibleCategories.map((entry) => String(entry).toUpperCase()))];
  if (compatibleCategories.length === 0 || compatibleCategories.some((entry) => !allowedDeviceCategories.has(entry))) {
    throw new Error("Compatible categories are invalid");
  }

  return {
    p_name: requiredString(payload.name, "Name", 2, 80),
    p_category: category,
    p_compatible_categories: compatibleCategories,
    p_description: optionalString(payload.description, 500),
    p_sku: optionalString(payload.sku, 64),
    p_minimum_stock_level: nonNegativeInteger(payload.minimumStockLevel, "Minimum stock level"),
    p_unit_cost: optionalMoney(payload.unitCost),
    p_selling_price: optionalMoney(payload.sellingPrice),
    p_supplier_name: optionalString(payload.supplierName, 120),
    p_supplier_contact: optionalString(payload.supplierContact, 160),
  };
}

async function listInventory(client: SupabaseClient) {
  const [itemsResult, activityResult] = await Promise.all([
    client.from("spare_parts").select(`
      id, name, category, compatible_categories, is_available, created_at, updated_at, archived_at,
      inventory_item_details(description, sku, minimum_stock_level, unit_cost, selling_price, supplier_name, supplier_contact),
      spare_part_stock(branch_id, quantity, updated_at)
    `).order("name"),
    client.from("inventory_adjustments").select(`
      id, request_id, part_id, branch_id, previous_quantity, new_quantity,
      adjustment_type, reason, performed_by_uid, performed_by_email, created_at,
      spare_parts(name)
    `).order("created_at", { ascending: false }).limit(20),
  ]);
  if (itemsResult.error || activityResult.error) throw new Error("Inventory query failed");

  const items = (itemsResult.data ?? []).map((row: any) => {
    const details = Array.isArray(row.inventory_item_details)
      ? row.inventory_item_details[0]
      : row.inventory_item_details;
    return {
      id: row.id,
      name: row.name,
      category: row.category,
      compatibleCategories: row.compatible_categories ?? [],
      isAvailable: row.is_available ?? true,
      description: details?.description ?? null,
      sku: details?.sku ?? null,
      minimumStockLevel: details?.minimum_stock_level ?? 0,
      unitCost: details?.unit_cost == null ? null : Number(details.unit_cost),
      sellingPrice: details?.selling_price == null ? null : Number(details.selling_price),
      supplierName: details?.supplier_name ?? null,
      supplierContact: details?.supplier_contact ?? null,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      archivedAt: row.archived_at,
      stocks: (row.spare_part_stock ?? []).map((stock: any) => ({
        branchId: stock.branch_id,
        quantity: stock.quantity,
        updatedAt: stock.updated_at,
      })),
    };
  });

  const recentAdjustments = (activityResult.data ?? []).map((row: any) => ({
    id: row.id,
    requestId: row.request_id,
    itemId: row.part_id,
    itemName: Array.isArray(row.spare_parts) ? row.spare_parts[0]?.name : row.spare_parts?.name,
    branchId: row.branch_id,
    previousQuantity: row.previous_quantity,
    newQuantity: row.new_quantity,
    adjustmentType: row.adjustment_type,
    reason: row.reason,
    performedByUid: row.performed_by_uid,
    performedByEmail: row.performed_by_email,
    createdAt: row.created_at,
  }));
  return { items, recentAdjustments };
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ message: "Method not allowed" }, 405);

  try {
    const admin = await verifyFirebaseAdmin(request, { projectId: FIREBASE_PROJECT_ID });
    if (!admin) return json({ message: "Admin authorization required" }, 403);

    const body = await request.json() as JsonRecord;
    const action = typeof body.action === "string" ? body.action : "";
    const payload = (body.payload && typeof body.payload === "object") ? body.payload as JsonRecord : {};
    const client = createClient(
      Deno.env.get("SUPABASE_URL")!,
      serviceRoleKey(),
      { auth: { persistSession: false, autoRefreshToken: false } },
    );

    if (action === "list") return json(await listInventory(client));

    if (action === "create_item") {
      const parameters = itemParameters(payload);
      const { error } = await client.rpc("inventory_admin_create_item", {
        p_item_id: uuid(body.requestId),
        ...parameters,
        p_admin_uid: admin.uid,
      });
      if (error) throw new Error("Create failed");
      return json({ ok: true });
    }

    if (action === "update_item") {
      const parameters = itemParameters(payload);
      const { error } = await client.rpc("inventory_admin_update_item", {
        p_item_id: uuid(payload.itemId, "item id"),
        ...parameters,
        p_is_available: payload.isAvailable === true,
        p_admin_uid: admin.uid,
      });
      if (error) throw new Error("Update failed");
      return json({ ok: true });
    }

    if (action === "set_availability") {
      const { error } = await client.rpc("inventory_admin_set_item_availability", {
        p_item_id: uuid(payload.itemId, "item id"),
        p_is_available: payload.isAvailable === true,
        p_admin_uid: admin.uid,
      });
      if (error) throw new Error("Availability update failed");
      return json({ ok: true });
    }

    if (action === "adjust_stock") {
      const adjustmentType = requiredString(payload.adjustmentType, "Adjustment type", 3, 10).toUpperCase();
      if (!allowedAdjustmentTypes.has(adjustmentType)) throw new Error("Adjustment type is invalid");
      const quantity = nonNegativeInteger(payload.quantity, "Quantity");
      if (adjustmentType !== "CORRECT" && quantity === 0) throw new Error("Quantity must be greater than zero");
      const { data, error } = await client.rpc("inventory_admin_adjust_stock", {
        p_request_id: uuid(body.requestId),
        p_part_id: uuid(payload.itemId, "item id"),
        p_branch_id: requiredString(payload.branchId, "Branch", 3, 20).toLowerCase(),
        p_adjustment_type: adjustmentType,
        p_quantity: quantity,
        p_reason: requiredString(payload.reason, "Reason", 3, 200),
        p_admin_uid: admin.uid,
        p_admin_email: admin.email,
      });
      if (error) throw new Error("Adjustment failed");
      return json({ ok: true, adjustment: data?.[0] ?? null });
    }

    return json({ message: "Unsupported inventory action" }, 400);
  } catch (error) {
    const message = error instanceof Error && (
      error.message.includes("invalid") ||
      error.message.includes("required") ||
      error.message.includes("too long") ||
      error.message.includes("greater than zero")
    ) ? error.message : "Unable to process the inventory request";
    return json({ message }, message === "Unable to process the inventory request" ? 500 : 400);
  }
});
