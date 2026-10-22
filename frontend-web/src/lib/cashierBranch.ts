import { useEffect, useState } from 'react';
import { cachedShift } from '@/components/OpenShiftModal';

/**
 * FUENTE ÚNICA DE VERDAD del "almacén/sucursal del cajero".
 *
 * Antes el POS usaba `localStorage['mbs.branchId']` y las pantallas de inventario
 * (Producción/Salidas) usaban la primera sucursal activa. Si no coincidían, el cajero
 * producía en una sucursal y el POS leía otra sin pan → cuadrícula vacía. Esta utilidad
 * unifica el criterio para todo el flujo del cajero.
 *
 * Prioridad (de más a menos autoritativa):
 *   1) la sucursal del TURNO ABIERTO del cajero (donde abrió su caja) — es la verdad
 *      operativa del día: se vende y se mueve inventario en ESA sucursal;
 *   2) la última sucursal elegida en el POS (`localStorage['mbs.branchId']`);
 *   3) la primera sucursal activa (respaldo, p. ej. multi-sucursal sin turno aún).
 *
 * Si el cajero solo tiene una sucursal, las tres coinciden de forma trivial.
 */

export const BRANCH_KEY = 'mbs.branchId';

/** Sucursal mínima necesaria para resolver el respaldo (primera activa). */
export interface BranchRef {
  id: number;
  active: boolean;
}

/** Lee el branchId guardado del POS (0 si no hay). */
export function storedBranchId(): number {
  return Number(localStorage.getItem(BRANCH_KEY)) || 0;
}

/** Persiste la sucursal elegida como la última usada por el cajero. */
export function rememberBranchId(branchId: number): void {
  if (branchId) localStorage.setItem(BRANCH_KEY, String(branchId));
}

/**
 * Resuelve el branchId del cajero a partir de las sucursales disponibles, aplicando la
 * prioridad descrita arriba. `preferred` permite respetar una elección explícita vigente
 * (p. ej. el selector del POS) siempre que siga siendo una sucursal activa válida.
 */
export function resolveCashierBranchId(branches: BranchRef[], preferred = 0): number {
  const active = branches.filter((b) => b.active);
  const isValid = (id: number) => id > 0 && active.some((b) => b.id === id);

  const fromShift = cachedShift()?.branchId ?? 0;
  if (isValid(fromShift)) return fromShift;
  if (isValid(preferred)) return preferred;
  const fromStored = storedBranchId();
  if (isValid(fromStored)) return fromStored;
  return active[0]?.id ?? 0;
}

/**
 * Hook que mantiene el branchId del cajero como estado y lo persiste. Expone un setter
 * para el selector de sucursal (multi-sucursal), que además actualiza la última usada.
 *
 * Al cargar/actualizar la lista de sucursales, re-resuelve con la prioridad única para
 * que POS e Inventario converjan al MISMO almacén del turno abierto.
 */
export function useCashierBranch(branches: BranchRef[]): {
  branchId: number;
  setBranchId: (id: number) => void;
} {
  const [branchId, setBranchIdState] = useState<number>(() => resolveCashierBranchId([], storedBranchId()));

  // Re-resuelve cuando cambian las sucursales (carga async) manteniendo la prioridad única.
  useEffect(() => {
    if (branches.length === 0) return;
    setBranchIdState((prev) => resolveCashierBranchId(branches, prev));
  }, [branches]);

  // Persiste la sucursal vigente como "última usada".
  useEffect(() => {
    rememberBranchId(branchId);
  }, [branchId]);

  const setBranchId = (id: number) => {
    setBranchIdState(id);
    rememberBranchId(id);
  };

  return { branchId, setBranchId };
}
