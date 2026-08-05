import type { ProductMaster } from "../api";

const storageKey = "adavis.iiot-master.product-master";

export const productMasterRows: ProductMaster[] = [
  {
    productId: "PRD-0001",
    productCode: "PARA-500",
    productName: "Paracetamol 500mg",
    tenantId: "TNT-0001",
    plantId: "PLANT-001",
    isActive: true,
    createdAt: "2026-01-20T10:30:00Z",
  },
];

export function getProductMasterRows() {
  if (typeof window === "undefined") return productMasterRows;

  try {
    const stored = window.localStorage.getItem(storageKey);
    if (!stored) return productMasterRows;
    const parsed = JSON.parse(stored);
    return Array.isArray(parsed) ? (parsed as ProductMaster[]) : productMasterRows;
  } catch {
    return productMasterRows;
  }
}

export function addProductMasterRow(row: ProductMaster) {
  if (typeof window === "undefined") return;

  const rows = getProductMasterRows();
  window.localStorage.setItem(
    storageKey,
    JSON.stringify([row, ...rows.filter((item) => item.productId !== row.productId)]),
  );
}
