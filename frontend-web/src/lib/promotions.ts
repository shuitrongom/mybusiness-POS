/**
 * Motor de PROMOCIONES del punto de venta.
 *
 * Dadas las promociones vigentes (traídas de /promotions/active) y los renglones del carrito,
 * calcula el descuento a aplicar por renglón. Soporta:
 *   • PERCENT       — porcentaje sobre el importe del renglón.
 *   • AMOUNT        — importe fijo por unidad.
 *   • SPECIAL_PRICE — fija un precio especial por unidad (descuenta la diferencia).
 *   • NXM           — "lleva N, paga M" (2x1, 3x2, …): regala las unidades gratis por cada grupo.
 *
 * Ámbitos: PRODUCT (productos de la promo), CATEGORY (categoría del producto) o ALL (todo).
 * Por cada renglón se elige la promoción de MAYOR prioridad que le aplique (no se acumulan
 * varias sobre el mismo renglón, para evitar descuentos incoherentes).
 */

export interface Promotion {
  id: number;
  name: string;
  type: 'PERCENT' | 'AMOUNT' | 'SPECIAL_PRICE' | 'NXM';
  scope: 'PRODUCT' | 'CATEGORY' | 'ALL';
  categoryId: number | null;
  value: number;
  buyQty: number;
  payQty: number;
  minAmount: number;
  priority: number;
  productIds: number[];
}

export interface PromoCartLine {
  productId: number;
  categoryId: number | null;
  quantity: number;
  unitPrice: number;
}

export interface PromoResult {
  /** Descuento por renglón, indexado por productId. */
  discountByProduct: Record<number, number>;
  /** Nombre de la promoción aplicada por renglón, para mostrar en la UI. */
  labelByProduct: Record<number, string>;
  /** Descuento total por promociones. */
  total: number;
}

function appliesTo(promo: Promotion, line: PromoCartLine): boolean {
  if (promo.scope === 'ALL') return true;
  if (promo.scope === 'CATEGORY') return line.categoryId != null && line.categoryId === promo.categoryId;
  return promo.productIds.includes(line.productId);
}

/** Descuento (en importe) que una promoción produce sobre un renglón. */
function discountFor(promo: Promotion, line: PromoCartLine): number {
  const lineAmount = line.quantity * line.unitPrice;
  if (promo.minAmount > 0 && lineAmount < promo.minAmount) return 0;

  switch (promo.type) {
    case 'PERCENT':
      return Math.min(lineAmount, (lineAmount * promo.value) / 100);
    case 'AMOUNT':
      return Math.min(lineAmount, promo.value * line.quantity);
    case 'SPECIAL_PRICE': {
      if (promo.value <= 0 || promo.value >= line.unitPrice) return 0;
      return (line.unitPrice - promo.value) * line.quantity;
    }
    case 'NXM': {
      const n = promo.buyQty;
      const m = promo.payQty;
      if (n <= 0 || m <= 0 || m >= n) return 0;
      const groups = Math.floor(line.quantity / n);
      const freeUnits = groups * (n - m);
      return freeUnits * line.unitPrice;
    }
    default:
      return 0;
  }
}

/** Aplica las promociones al carrito y devuelve los descuentos por renglón. */
export function applyPromotions(promos: Promotion[], lines: PromoCartLine[]): PromoResult {
  const discountByProduct: Record<number, number> = {};
  const labelByProduct: Record<number, string> = {};
  let total = 0;

  const ordered = [...promos].sort((a, b) => b.priority - a.priority);

  for (const line of lines) {
    let best = 0;
    let bestLabel = '';
    for (const promo of ordered) {
      if (!appliesTo(promo, line)) continue;
      const d = discountFor(promo, line);
      if (d > best) { best = d; bestLabel = promo.name; }
    }
    if (best > 0) {
      discountByProduct[line.productId] = Math.round(best * 100) / 100;
      labelByProduct[line.productId] = bestLabel;
      total += discountByProduct[line.productId];
    }
  }

  return { discountByProduct, labelByProduct, total: Math.round(total * 100) / 100 };
}
