import { useEffect, useMemo, useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { ArrowDownToLine, ArrowUpFromLine, Plus, Minus, Trash2, Check, PackageOpen, TriangleAlert } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { useSession } from '@/store/session';
import { decodeToken, hasModule } from '@/lib/jwt';
import { ProductPicker } from '@/components/ProductPicker';
import { InfoBanner } from '@/components/InfoBanner';
import '@/pages/dashboard.css';
import '@/pages/inventory-doc.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

// Fecha + hora local (ej. "16/10/2026 · 09:52 a.m."). Usa createdAt (instante exacto del registro).
function dateTime(iso: string | null): { date: string; time: string } {
  if (!iso) return { date: '—', time: '' };
  const d = new Date(iso);
  if (isNaN(d.getTime())) return { date: '—', time: '' };
  return {
    date: d.toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric' }),
    time: d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' }),
  };
}

// Avatar de artículo: color estable e iniciales a partir del nombre (acabado premium del renglón).
const AVATAR_COLORS = ['#2e6ef2', '#12b886', '#f59e0b', '#e11d48', '#7c5cff', '#0ea5e9', '#d97706', '#0891b2'];
function avatarColor(text: string): string {
  let hash = 0;
  for (let i = 0; i < text.length; i++) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}
function avatarInitials(name: string): string {
  const w = name.trim().split(/\s+/).filter(Boolean);
  if (w.length === 0) return '?';
  if (w.length === 1) return w[0].slice(0, 2).toUpperCase();
  return (w[0][0] + w[1][0]).toUpperCase();
}

interface Concept { id: number; name: string; direction: string; }
interface Product { id: number; name: string; price: number; cost: number; unit: string; }
interface Branch { id: number; name: string; active: boolean; }
interface Line { productId: number; description: string; quantity: number; unitCost: number; entryPrice: number; unit: string; }
interface DocRow {
  id: number; folio: string; conceptName: string | null; branchName: string | null;
  totalQty: number; totalValue: number; createdBy: string | null; createdAt: string;
}
/**
 * Fila del historial UNIFICADO de "Salidas recientes" de panadería (GET /bakery/exits):
 * salida normal (su concepto real) + merma MANUAL ("Paso a merma") + merma REPARTO
 * ("Reparto de merma"). El backend ya resuelve el tipo y el orden; el front solo renderiza.
 */
type ExitKind = 'NORMAL' | 'MANUAL' | 'REPARTO';
interface ExitRow {
  id: string;            // 'DOC-12' | 'WASTE-7' (clave estable única entre fuentes)
  kind: ExitKind;
  concept: string;       // 'Salida' | 'Paso a merma' | 'Reparto de merma' | concepto real
  folio: string | null;
  branchName: string | null;
  totalQty: number;
  totalValue: number;
  cashier: string | null;
  createdAt: string;     // instante ISO
}

/**
 * Etiqueta + color del chip de tipo de salida. Reutiliza los mismos colores que el
 * "Reporte de merma" (BakeryWastePage): ámbar = paso a merma, sky = reparto, neutro = salida.
 */
const EXIT_KIND_LABEL: Record<ExitKind, string> = {
  NORMAL: 'Salida',
  MANUAL: 'Paso a merma',
  REPARTO: 'Reparto de merma',
};
function exitKindColor(kind: ExitKind): string {
  switch (kind) {
    case 'MANUAL': return '#d97706';
    case 'REPARTO': return '#0ea5e9';
    default: return '#64748b';
  }
}

/** Fila por renglón de GET /bakery/productions (historial de producción: folio + artículo). */
interface ProductionRow {
  productionId: number;
  folio: string | null;
  productName: string | null;
  description: string | null;
  quantity: number | null;
  unit: string | null;
  entryPrice: number | null;
  lineTotal: number | null;
  productionDate: string | null;
  createdAt: string | null;
}

/**
 * Documento de ENTRADA o SALIDA de inventario. Reutilizable: recibe el tipo (IN/OUT). Elige
 * concepto (motivo), almacén y captura renglones. Al finalizar afecta las existencias.
 */
export function InventoryDocPage({ docType }: { docType: 'IN' | 'OUT' }) {
  const isIn = docType === 'IN';
  // En giro panadería, la ENTRADA de inventario es la PRODUCCIÓN del día: captura precio de entrada
  // por renglón, estatus fijo "Producción" (sin selector) y envía a POST /bakery/productions.
  const isBakery = hasModule(decodeToken(useSession((s) => s.token)), 'bakery');
  const isProduction = isIn && isBakery;

  // En giro panadería, la SALIDA ofrece además dos acciones de merma: pasar pan del stock normal
  // a merma (MANUAL) o sacar merma hacia un repartidor (REPARTO). 'NORMAL' = salida genérica.
  type ExitMode = 'NORMAL' | 'MANUAL' | 'REPARTO';
  const [exitMode, setExitMode] = useState<ExitMode>('NORMAL');
  const isWasteExit = !isIn && isBakery && (exitMode === 'MANUAL' || exitMode === 'REPARTO');
  const [concepts, setConcepts] = useState<Concept[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [conceptId, setConceptId] = useState<number>(0);
  const [branchId, setBranchId] = useState<number>(0);
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [history, setHistory] = useState<DocRow[]>([]);
  const [prodHistory, setProdHistory] = useState<ProductionRow[]>([]);
  // Historial UNIFICADO de salidas de panadería (salida normal + merma MANUAL/REPARTO).
  const [exits, setExits] = useState<ExitRow[]>([]);
  const [busy, setBusy] = useState(false);
  // Existencia EN MERMA por producto (para el modo reparto): clave productId → cantidad disponible.
  const [wasteStocks, setWasteStocks] = useState<Record<number, number>>({});
  // Productos CON EXISTENCIA para el selector de Salidas (solo lo que tiene inventario).
  const [availableProducts, setAvailableProducts] = useState<Product[]>([]);
  // Existencia disponible por artículo (según el modo: stock normal o merma), para mostrarla y
  // bloquear que se saque más de lo que hay. Clave productId → cantidad disponible.
  const [availableQty, setAvailableQty] = useState<Record<number, number>>({});
  // Nombres de artículos sin precio que bloquean la producción (dispara el modal de aviso).
  const [missingPrice, setMissingPrice] = useState<string[]>([]);

  // SALIDA en panadería: historial UNIFICADO (salida normal + merma MANUAL/REPARTO) desde el
  // endpoint nuevo. El backend une, ordena y limita; aquí solo se renderiza.
  const isBakeryExit = !isIn && isBakery;

  const loadHistory = () => {
    if (isProduction) {
      api.get<ProductionRow[]>('/bakery/productions', { params: { limit: 15 } })
        .then((r) => setProdHistory(r.data)).catch(() => setProdHistory([]));
      return;
    }
    if (isBakeryExit) {
      if (!branchId) { setExits([]); return; }
      api.get<ExitRow[]>('/bakery/exits', { params: { branchId, limit: 15 } })
        .then((r) => setExits(r.data)).catch(() => setExits([]));
      return;
    }
    api.get<DocRow[]>('/inventory/documents', { params: { docType, limit: 15 } })
      .then((r) => setHistory(r.data)).catch(() => setHistory([]));
  };

  useEffect(() => {
    (async () => {
      try {
        const [con, prod, br] = await Promise.all([
          api.get<Concept[]>('/inventory/concepts', { params: { direction: docType } }),
          api.get<Product[]>('/catalog/products'),
          api.get<Branch[]>('/branches'),
        ]);
        setConcepts(con.data);
        setProducts(prod.data);
        const active = br.data.filter((b) => b.active);
        setBranches(active); if (active[0]) setBranchId(active[0].id);
      } catch { toast.error('No se pudo cargar el catálogo'); }
    })();
    loadHistory();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [docType]);

  // En SALIDA de panadería el historial depende de la sucursal (el endpoint filtra por branchId),
  // que se resuelve async al cargar sucursales. Recarga cuando cambie branchId SOLO en este modo
  // (los demás modos no dependen de branchId, así que no se recargan de más).
  useEffect(() => {
    if (isBakeryExit && branchId) loadHistory();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isBakeryExit, branchId]);

  // En modo REPARTO, carga la existencia de merma de cada producto del carrito para mostrar cuánto
  // hay disponible y evitar sobre-repartir. Se recalcula al cambiar renglones, sucursal o modo.
  useEffect(() => {
    if (exitMode !== 'REPARTO' || !branchId || lines.length === 0) { setWasteStocks({}); return; }
    let cancelled = false;
    Promise.all(lines.map((l) =>
      api.get<{ quantity: number }>('/bakery/waste/stock', { params: { productId: l.productId, branchId } })
        .then((r) => [l.productId, r.data.quantity] as const)
        .catch(() => [l.productId, 0] as const)))
      .then((pairs) => { if (!cancelled) setWasteStocks(Object.fromEntries(pairs)); });
    return () => { cancelled = true; };
  }, [exitMode, branchId, lines]);

  // SALIDAS en giro panadería: el selector solo muestra panes CON EXISTENCIA (reparto = merma;
  // merma manual / salida normal = stock normal). Así el cajero no elige panes sin inventario.
  useEffect(() => {
    if (isIn || !isBakery || !branchId) { setAvailableProducts([]); return; }
    const kind = exitMode === 'REPARTO' ? 'waste' : 'normal';
    let cancelled = false;
    api.get<{ productId: number; productName: string; unit: string | null; quantity: number }[]>(
      '/bakery/stock/available', { params: { branchId, kind } })
      .then((r) => {
        if (cancelled) return;
        setAvailableProducts(r.data.map((x) => ({
          id: x.productId, name: x.productName, price: 0, cost: 0, unit: x.unit || 'pz',
        })));
        const qty: Record<number, number> = {};
        for (const x of r.data) qty[x.productId] = Number(x.quantity) || 0;
        setAvailableQty(qty);
      })
      .catch(() => { if (!cancelled) { setAvailableProducts([]); setAvailableQty({}); } });
    return () => { cancelled = true; };
  }, [isIn, isBakery, branchId, exitMode]);

  const selectedIds = useMemo(() => new Set(lines.map((l) => l.productId)), [lines]);

  const add = (p: { id: number; name: string; unit?: string | null; price?: number | null; cost?: number | null }) => {
    setLines((prev) => prev.some((l) => l.productId === p.id) ? prev
      // Nace sin cantidad (0 = placeholder "1"); el cajero la captura. Precio de venta prellenado
      // desde el producto; costo unitario desde su costo.
      : [...prev, { productId: p.id, description: p.name, quantity: 0, unitCost: p.cost || 0, entryPrice: p.price || 0, unit: p.unit || '' }]);
  };

  // Lista para el selector: en SALIDAS de panadería, solo panes con existencia; en el resto, catálogo.
  const pickerProducts = (!isIn && isBakery) ? availableProducts : products;
  const setLine = (i: number, patch: Partial<Line>) => setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const removeLine = (i: number) => setLines((prev) => prev.filter((_, idx) => idx !== i));

  const totalUnits = lines.reduce((s, l) => s + l.quantity, 0);
  const totalValue = lines.reduce((s, l) => s + l.quantity * (isProduction ? l.entryPrice : l.unitCost), 0);

  const submit = async () => {
    if (lines.length === 0) { toast.info('Agrega al menos un producto'); return; }
    // Regla de negocio: no se da ENTRADA/PRODUCCIÓN a un artículo sin precio. El precio lo define
    // la ficha del producto; si está en 0, hay que capturarlo primero (modal), no registrar en 0.
    if (isProduction) {
      const sinPrecio = lines.filter((l) => !(l.entryPrice > 0)).map((l) => l.description);
      if (sinPrecio.length > 0) {
        setMissingPrice(sinPrecio);
        return;
      }
    }
    const sinCantidad = lines.find((l) => l.quantity < 1);
    if (sinCantidad) {
      toast.error('Falta la cantidad', `Captura la cantidad de «${sinCantidad.description}» (mínimo 1).`);
      return;
    }
    setBusy(true);
    try {
      // No se saca más de lo disponible (segunda barrera además del backend). Avisa cuál se pasa.
      if (!isIn && isBakery) {
        const over = lines.find((l) => {
          const a = exitMode === 'REPARTO' ? (wasteStocks[l.productId] ?? availableQty[l.productId]) : availableQty[l.productId];
          return a != null && l.quantity > a;
        });
        if (over) {
          const a = exitMode === 'REPARTO' ? (wasteStocks[over.productId] ?? availableQty[over.productId]) : availableQty[over.productId];
          toast.error('Cantidad mayor a la disponible',
            `De «${over.description}» solo hay ${a ?? 0} disponible(s). Ajusta la cantidad.`);
          setBusy(false);
          return;
        }
      }
      if (isWasteExit) {
        // Merma manual (del stock normal a merma) o reparto (de la merma hacia el repartidor).
        // Un renglón por transferencia; el backend valida la no-sobreventa de cada origen.
        for (const l of lines) {
          await api.post('/bakery/waste/transfer', {
            productId: l.productId, branchId, quantity: l.quantity, origin: exitMode,
          });
        }
        toast.success(
          exitMode === 'MANUAL' ? 'Pan pasado a merma' : 'Reparto de merma registrado',
          exitMode === 'MANUAL'
            ? 'El pan seleccionado salió del stock normal y entró a merma.'
            : 'La merma se descontó y quedó registrada como salida a repartidor.');
      } else if (isProduction) {
        await api.post('/bakery/productions', {
          branchId, statusCode: 'PRODUCED', notes: notes || null,
          lines: lines.map((l) => ({ productId: l.productId, description: l.description, quantity: l.quantity, unit: l.unit || null, entryPrice: l.entryPrice, unitCost: l.unitCost })),
        });
        toast.success('Producción registrada', 'Se actualizaron las existencias del día.');
      } else {
        await api.post('/inventory/documents', {
          docType, conceptId: conceptId || null, branchId, notes: notes || null,
          lines: lines.map((l) => ({ productId: l.productId, description: l.description, quantity: l.quantity, unitCost: l.unitCost })),
        });
        toast.success(isIn ? 'Entrada registrada' : 'Salida registrada', 'Se actualizó el inventario.');
      }
      setLines([]); setNotes('');
      loadHistory();
    } catch { toast.error(isProduction ? 'No se pudo registrar la producción' : 'No se pudo registrar el movimiento'); } finally { setBusy(false); }
  };

  const Icon = isIn ? ArrowDownToLine : ArrowUpFromLine;

  return (
    <div>
      <h1 className="page-title">{isProduction ? 'Entradas / Producción' : isIn ? 'Entradas al inventario' : 'Salidas al inventario'}</h1>
      <p className="page-sub">{isProduction ? 'Registra la producción de pan del día con su precio de entrada' : isIn ? 'Registra entradas de mercancía con su concepto' : isBakery ? 'Registra salidas: merma manual, reparto de merma o salida normal' : 'Registra salidas de mercancía con su concepto'}</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="pur-head">
          {!isIn && isBakery && (
            <label className="field"><span>Tipo de salida</span>
              <select value={exitMode} onChange={(e) => setExitMode(e.target.value as ExitMode)}>
                <option value="MANUAL">Pasar a merma (manual)</option>
                <option value="REPARTO">Reparto de merma (a repartidor)</option>
                <option value="NORMAL">Salida normal (con concepto)</option>
              </select></label>
          )}
          {!isProduction && !isWasteExit && (
            <label className="field"><span>Concepto</span>
              <select value={conceptId} onChange={(e) => setConceptId(Number(e.target.value))}>
                <option value={0}>Selecciona…</option>
                {concepts.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select></label>
          )}
          <label className="field"><span>Almacén / sucursal</span>
            <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select></label>
          <label className="field" style={{ gridColumn: 'span 2' }}><span>Observaciones</span>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
        </div>

        {/* Banner de contexto: explica en una línea qué hace esta pantalla / modo. */}
        {isProduction && (
          <InfoBanner icon={<ArrowDownToLine size={18} />} tone="brand" title="Producción del día">
            Registra el pan que horneaste hoy. Suma la existencia vendible y toma el precio de venta
            de la ficha de cada artículo (no editable aquí).
          </InfoBanner>
        )}
        {isIn && !isBakery && (
          <InfoBanner icon={<ArrowDownToLine size={18} />} tone="brand" title="Entrada al inventario">
            Registra la entrada de mercancía con su concepto. Suma la existencia del almacén.
          </InfoBanner>
        )}
        {!isIn && isBakery && exitMode === 'MANUAL' && (
          <InfoBanner icon={<Trash2 size={18} />} tone="amber" title="Pasar pan a merma (manual)">
            El pan sale del stock vendible y entra a MERMA. Úsalo para pan del día anterior o dañado
            que ya no se vende a precio normal. Solo aparecen artículos con existencia.
          </InfoBanner>
        )}
        {!isIn && isBakery && exitMode === 'REPARTO' && (
          <InfoBanner icon={<ArrowUpFromLine size={18} />} tone="sky" title="Reparto de merma a repartidor">
            Descuenta pan que YA está en merma y lo registra como entregado a un repartidor. Solo
            aparecen artículos con existencia en merma.
          </InfoBanner>
        )}
        {!isIn && isBakery && exitMode === 'NORMAL' && (
          <InfoBanner icon={<PackageOpen size={18} />} tone="neutral" title="Salida normal de inventario">
            Salida con un concepto (consumo, ajuste, etc.). Descuenta del stock normal. Solo aparecen
            artículos con existencia.
          </InfoBanner>
        )}
        {!isIn && !isBakery && (
          <InfoBanner icon={<ArrowUpFromLine size={18} />} tone="neutral" title="Salida del inventario">
            Registra la salida de mercancía con su concepto. Descuenta la existencia del almacén.
          </InfoBanner>
        )}

        <div className="pur-add" style={{ marginTop: 'var(--space-4)' }}>
          <ProductPicker
            products={pickerProducts}
            onPick={add}
            selectedIds={selectedIds}
            placeholder={isProduction ? 'Elegir pan a producir…'
              : exitMode === 'REPARTO' ? 'Elegir pan en merma…'
                : isWasteExit ? 'Elegir pan a pasar a merma…'
                  : 'Elegir producto…'}
          />
        </div>

        {/* Grid de columnas compartido por cabecera y filas, armado según el modo para que todo
            quede perfectamente alineado por construcción. */}
        {(() => {
          // En salidas de panadería, SIEMPRE se muestra el disponible del artículo (stock normal
          // para MANUAL/NORMAL, merma para REPARTO), para que el cajero sepa qué hay.
          const showAvail = !isIn && isBakery;
          const availLabel = exitMode === 'REPARTO' ? 'Disponible (merma)' : 'Existencia';
          const cols = [
            'minmax(0, 1fr)',                 // Artículo
            '136px',                          // Cantidad (stepper)
            ...(isProduction ? ['128px'] : []), // Precio de venta
            ...(showAvail ? ['150px'] : []),  // Disponible / Existencia
            ...(!isWasteExit ? ['128px'] : []), // Costo unitario
            ...(!isWasteExit ? ['110px'] : []), // Importe
            '44px',                           // Acción
          ].join(' ');
          return (
            <div className="idoc-lines">
              <div className="idoc-row-head" style={{ gridTemplateColumns: cols }}>
                <span>Artículo</span>
                <span className="idoc-h-right">Cantidad</span>
                {isProduction && <span className="idoc-h-right">Precio de venta</span>}
                {showAvail && <span className="idoc-h-right">{availLabel}</span>}
                {!isWasteExit && <span className="idoc-h-right">Costo unitario</span>}
                {!isWasteExit && <span className="idoc-h-right">Importe</span>}
                <span />
              </div>

              {lines.map((l, i) => {
                // Disponible del artículo según el modo: REPARTO usa merma (wasteStocks), MANUAL y
                // NORMAL usan el stock normal (availableQty). Si pides más de lo que hay, se marca.
                const avail = exitMode === 'REPARTO'
                  ? (wasteStocks[l.productId] ?? availableQty[l.productId])
                  : availableQty[l.productId];
                const over = showAvail && avail != null && l.quantity > avail;
                return (
                  <div className="idoc-row" key={l.productId} style={{ gridTemplateColumns: cols }}>
                    <div className="idoc-article">
                      <span className="idoc-article-avatar" style={{ background: avatarColor(l.description) }}>
                        {avatarInitials(l.description)}
                      </span>
                      <div className="idoc-article-text">
                        <div className="idoc-article-name">{l.description}</div>
                        <div className="idoc-article-unit">{l.unit || 'pieza'}</div>
                      </div>
                    </div>

                    <div className={`idoc-qty ${l.quantity < 1 ? 'is-empty' : ''}`}>
                      <button type="button" aria-label="Menos" disabled={l.quantity <= 1}
                        onClick={() => setLine(i, { quantity: Math.max(1, l.quantity - 1) })}>
                        <Minus size={14} />
                      </button>
                      {/* Placeholder "1" cuando aún no hay cantidad (0); nunca se registra en 0. */}
                      <input type="number" min={1} step={1} placeholder="1"
                        value={l.quantity > 0 ? l.quantity : ''}
                        onChange={(e) => {
                          const v = Math.floor(Number(e.target.value));
                          setLine(i, { quantity: Number.isFinite(v) && v > 0 ? v : 0 });
                        }} />
                      <button type="button" aria-label="Más"
                        onClick={() => setLine(i, { quantity: Math.max(1, l.quantity) + 1 })}>
                        <Plus size={14} />
                      </button>
                    </div>

                    {isProduction && (
                      // El precio de venta lo define la ficha del producto; NO se edita aquí.
                      // Si el artículo no tiene precio, se marca para obligar a capturarlo primero.
                      l.entryPrice > 0 ? (
                        <span className="idoc-fixed-price" title="Precio de venta del artículo (no editable aquí)">
                          {money(l.entryPrice)}
                        </span>
                      ) : (
                        <span className="idoc-fixed-price is-missing" title="Este artículo no tiene precio. Captúralo en su ficha.">
                          Sin precio
                        </span>
                      )
                    )}

                    {showAvail && (
                      <span className={`idoc-availcell ${avail != null && avail > 0 ? (over ? 'is-over' : 'is-ok') : 'is-none'}`}>
                        {avail == null
                          ? '…'
                          : avail > 0
                            ? <>{avail} <span className="idoc-availcell-unit">{l.unit || 'pz'}</span>{over && ' ⚠'}</>
                            : 'Sin existencia'}
                      </span>
                    )}

                    {!isWasteExit && (
                      <label className="idoc-money">
                        <span className="idoc-money-sign">$</span>
                        <input type="number" min={0} step="0.01" placeholder="0.00"
                          value={l.unitCost || ''}
                          onChange={(e) => setLine(i, { unitCost: Number(e.target.value) || 0 })} />
                      </label>
                    )}

                    {!isWasteExit && (
                      <span className="idoc-amount">{money(l.quantity * (isProduction ? l.entryPrice : l.unitCost))}</span>
                    )}

                    <button type="button" className="idoc-remove" aria-label="Quitar" onClick={() => removeLine(i)}>
                      <Trash2 size={15} />
                    </button>
                  </div>
                );
              })}

              {lines.length === 0 && (
                <div className="idoc-empty">
                  <PackageOpen size={26} strokeWidth={1.5} />
                  <span>Elige un artículo arriba para agregarlo al documento.</span>
                </div>
              )}
            </div>
          );
        })()}

        <div className="pur-foot">
          <div />
          <div className="pur-totals">
            <div className="pur-total-row"><span>Unidades</span><span>{totalUnits}</span></div>
            {!isWasteExit && <div className="pur-total-row pur-total-grand"><span>Valor</span><strong>{money(totalValue)}</strong></div>}
            <div className="pur-actions">
              <button className="btn-accent" disabled={busy} onClick={submit}>
                <Check size={16} /> {isWasteExit
                  ? (exitMode === 'MANUAL' ? 'Pasar a merma' : 'Registrar reparto')
                  : isProduction ? 'Registrar producción' : isIn ? 'Registrar entrada' : 'Registrar salida'}
              </button>
            </div>
          </div>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Icon size={18} /> {isProduction ? 'Producción' : isIn ? 'Entradas' : 'Salidas'} recientes</h3>
        {isProduction ? (
          <div className="idoc-hist">
            {(() => {
              const cols = '96px minmax(150px, 1fr) 90px 100px 110px 140px';
              return (
                <>
                  <div className="idoc-hist-head" style={{ gridTemplateColumns: cols }}>
                    <span>Folio</span>
                    <span>Artículo</span>
                    <span className="idoc-hist-right">Cantidad</span>
                    <span className="idoc-hist-right">Precio</span>
                    <span className="idoc-hist-right">Importe</span>
                    <span className="idoc-hist-right">Registrado</span>
                  </div>
                  <div className="idoc-hist-body">
                    {prodHistory.map((r, i) => (
                      <div className="idoc-hist-row" key={`${r.productionId}-${i}`} style={{ gridTemplateColumns: cols }}>
                        <span className="idoc-hist-folio">{r.folio || '—'}</span>
                        <span className="idoc-hist-name">
                          {r.productName || r.description || <span className="idoc-hist-muted">—</span>}
                          <span className="idoc-hist-muted" style={{ fontWeight: 400 }}> · {r.quantity ?? 0} {r.unit || 'pz'}</span>
                        </span>
                        <span className="idoc-hist-num">{r.quantity ?? 0}</span>
                        <span className="idoc-hist-num">{money(r.entryPrice || 0)}</span>
                        <span className="idoc-hist-amount">{money(r.lineTotal || 0)}</span>
                        {(() => {
                          const dt = dateTime(r.createdAt);
                          return (
                            <span className="idoc-hist-when">
                              <span className="idoc-hist-when-date">{dt.date}</span>
                              {dt.time && <span className="idoc-hist-when-time">{dt.time}</span>}
                            </span>
                          );
                        })()}
                      </div>
                    ))}
                    {prodHistory.length === 0 && <div className="idoc-hist-empty">Sin producciones registradas.</div>}
                  </div>
                </>
              );
            })()}
          </div>
        ) : isBakeryExit ? (
          // SALIDAS panadería: lista UNIFICADA con el TIPO visible por fila (chip de color).
          // El concepto ya viene resuelto ("Paso a merma" / "Reparto de merma" / concepto real),
          // así ninguna fila queda en "—" como antes. Grid compartido cabecera/filas.
          <div className="idoc-hist">
            {(() => {
              const cols = '150px 110px minmax(150px, 1fr) 150px 100px 130px 150px';
              return (
                <>
                  <div className="idoc-hist-head" style={{ gridTemplateColumns: cols }}>
                    <span>Tipo</span>
                    <span>Folio</span>
                    <span>Concepto</span>
                    <span>Almacén</span>
                    <span className="idoc-hist-right">Unidades</span>
                    <span className="idoc-hist-right">Valor</span>
                    <span>Registró</span>
                  </div>
                  <div className="idoc-hist-body">
                    {exits.map((r) => (
                      <div className="idoc-hist-row" key={r.id} style={{ gridTemplateColumns: cols }}>
                        <span className="idoc-hist-chip" style={{ background: exitKindColor(r.kind) }}>
                          {EXIT_KIND_LABEL[r.kind]}
                        </span>
                        {r.folio
                          ? <span className="idoc-hist-folio">{r.folio}</span>
                          : <span className="idoc-hist-muted">—</span>}
                        <span className="idoc-hist-name">{r.concept}</span>
                        <span>{r.branchName || '—'}</span>
                        <span className="idoc-hist-num">{r.totalQty}</span>
                        <span className="idoc-hist-amount">{money(r.totalValue)}</span>
                        <span className="idoc-hist-muted">{r.cashier || '—'}</span>
                      </div>
                    ))}
                    {exits.length === 0 && <div className="idoc-hist-empty">Sin salidas registradas.</div>}
                  </div>
                </>
              );
            })()}
          </div>
        ) : (
          <div className="idoc-hist">
            {(() => {
              const cols = '110px minmax(160px, 1fr) 160px 110px 140px 150px';
              return (
                <>
                  <div className="idoc-hist-head" style={{ gridTemplateColumns: cols }}>
                    <span>Folio</span>
                    <span>Concepto</span>
                    <span>Almacén</span>
                    <span className="idoc-hist-right">Unidades</span>
                    <span className="idoc-hist-right">Valor</span>
                    <span>Registró</span>
                  </div>
                  <div className="idoc-hist-body">
                    {history.map((d) => (
                      <div className="idoc-hist-row" key={d.id} style={{ gridTemplateColumns: cols }}>
                        <span className="idoc-hist-folio">{d.folio}</span>
                        <span className="idoc-hist-name">{d.conceptName || <span className="idoc-hist-muted">—</span>}</span>
                        <span>{d.branchName || '—'}</span>
                        <span className="idoc-hist-num">{d.totalQty}</span>
                        <span className="idoc-hist-amount">{money(d.totalValue)}</span>
                        <span className="idoc-hist-muted">{d.createdBy || '—'}</span>
                      </div>
                    ))}
                    {history.length === 0 && <div className="idoc-hist-empty">Sin movimientos registrados.</div>}
                  </div>
                </>
              );
            })()}
          </div>
        )}
      </motion.div>

      {/* Modal: no se puede dar producción a artículos sin precio. Guía al cajero a capturarlo. */}
      <AnimatePresence>
        {missingPrice.length > 0 && (
          <motion.div className="idoc-modal-overlay"
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => setMissingPrice([])}>
            <motion.div className="idoc-modal" onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97 }} transition={{ duration: 0.18 }}>
              <div className="idoc-modal-icon"><TriangleAlert size={26} /></div>
              <h3 className="idoc-modal-title">Captura primero el precio</h3>
              <p className="idoc-modal-text">
                No se puede registrar la producción con precio en 0. Estos artículos no tienen precio
                de venta configurado:
              </p>
              <ul className="idoc-modal-list">
                {missingPrice.map((n) => <li key={n}>{n}</li>)}
              </ul>
              <p className="idoc-modal-hint">
                Ve a <strong>Productos</strong>, abre el artículo y captura su <strong>precio de venta</strong>
                en la pestaña Precios. Luego vuelve a registrar la producción.
              </p>
              <div className="idoc-modal-actions">
                <button className="btn-accent" onClick={() => setMissingPrice([])}>Entendido</button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export function InventoryEntriesPage() { return <InventoryDocPage docType="IN" />; }
export function InventoryExitsPage() { return <InventoryDocPage docType="OUT" />; }
