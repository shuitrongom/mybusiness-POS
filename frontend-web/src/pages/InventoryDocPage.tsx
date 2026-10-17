import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { ArrowDownToLine, ArrowUpFromLine, Plus, Minus, Trash2, Check, PackageOpen } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { useSession } from '@/store/session';
import { decodeToken, hasModule } from '@/lib/jwt';
import { ProductPicker } from '@/components/ProductPicker';
import '@/pages/dashboard.css';
import '@/pages/inventory-doc.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

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
  const [busy, setBusy] = useState(false);
  // Existencia EN MERMA por producto (para el modo reparto): clave productId → cantidad disponible.
  const [wasteStocks, setWasteStocks] = useState<Record<number, number>>({});
  // Productos CON EXISTENCIA para el selector de Salidas (solo lo que tiene inventario).
  const [availableProducts, setAvailableProducts] = useState<Product[]>([]);

  const loadHistory = () => {
    if (isProduction) {
      api.get<ProductionRow[]>('/bakery/productions', { params: { limit: 15 } })
        .then((r) => setProdHistory(r.data)).catch(() => setProdHistory([]));
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
      })
      .catch(() => { if (!cancelled) setAvailableProducts([]); });
    return () => { cancelled = true; };
  }, [isIn, isBakery, branchId, exitMode]);

  const selectedIds = useMemo(() => new Set(lines.map((l) => l.productId)), [lines]);

  const add = (p: { id: number; name: string; unit?: string | null; price?: number | null; cost?: number | null }) => {
    setLines((prev) => prev.some((l) => l.productId === p.id) ? prev
      // Precio de venta prellenado desde el producto (editable); costo unitario desde su costo.
      : [...prev, { productId: p.id, description: p.name, quantity: 1, unitCost: p.cost || 0, entryPrice: p.price || 0, unit: p.unit || '' }]);
  };

  // Lista para el selector: en SALIDAS de panadería, solo panes con existencia; en el resto, catálogo.
  const pickerProducts = (!isIn && isBakery) ? availableProducts : products;
  const setLine = (i: number, patch: Partial<Line>) => setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const removeLine = (i: number) => setLines((prev) => prev.filter((_, idx) => idx !== i));

  const totalUnits = lines.reduce((s, l) => s + l.quantity, 0);
  const totalValue = lines.reduce((s, l) => s + l.quantity * (isProduction ? l.entryPrice : l.unitCost), 0);

  const submit = async () => {
    if (lines.length === 0) { toast.info('Agrega al menos un producto'); return; }
    // Precio y costo son opcionales por ahora. Solo se exige cantidad mínima 1 por renglón.
    if (lines.some((l) => l.quantity < 1)) {
      toast.error('Cantidad inválida', 'La cantidad mínima por renglón es 1.');
      return;
    }
    setBusy(true);
    try {
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
          const cols = [
            'minmax(0, 1fr)',                 // Artículo
            '136px',                          // Cantidad (stepper)
            ...(isProduction ? ['128px'] : []), // Precio de venta
            ...(exitMode === 'REPARTO' ? ['120px'] : []), // Disponible en merma
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
                {exitMode === 'REPARTO' && <span className="idoc-h-right">Disponible</span>}
                {!isWasteExit && <span className="idoc-h-right">Costo unitario</span>}
                {!isWasteExit && <span className="idoc-h-right">Importe</span>}
                <span />
              </div>

              {lines.map((l, i) => {
                const avail = wasteStocks[l.productId];
                const overReparto = exitMode === 'REPARTO' && avail != null && l.quantity > avail;
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

                    <div className="idoc-qty">
                      <button type="button" aria-label="Menos" disabled={l.quantity <= 1}
                        onClick={() => setLine(i, { quantity: Math.max(1, l.quantity - 1) })}>
                        <Minus size={14} />
                      </button>
                      <input type="number" min={1} step={1} value={l.quantity}
                        onChange={(e) => setLine(i, { quantity: Math.max(1, Math.floor(Number(e.target.value) || 1)) })} />
                      <button type="button" aria-label="Más"
                        onClick={() => setLine(i, { quantity: l.quantity + 1 })}>
                        <Plus size={14} />
                      </button>
                    </div>

                    {isProduction && (
                      <label className="idoc-money">
                        <span className="idoc-money-sign">$</span>
                        <input type="number" min={0} step="0.01" placeholder="0.00"
                          value={l.entryPrice || ''}
                          onChange={(e) => setLine(i, { entryPrice: Number(e.target.value) || 0 })} />
                      </label>
                    )}

                    {exitMode === 'REPARTO' && (
                      <span className={`idoc-avail ${overReparto ? 'is-over' : ''}`}>
                        {avail == null ? '…' : avail}{overReparto ? ' ⚠' : ''}
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
              const cols = '96px minmax(160px, 1fr) 100px 110px 120px 120px';
              return (
                <>
                  <div className="idoc-hist-head" style={{ gridTemplateColumns: cols }}>
                    <span>Folio</span>
                    <span>Artículo</span>
                    <span className="idoc-hist-right">Cantidad</span>
                    <span className="idoc-hist-right">Precio</span>
                    <span className="idoc-hist-right">Importe</span>
                    <span className="idoc-hist-right">Fecha</span>
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
                        <span className="idoc-hist-num idoc-hist-muted" style={{ fontWeight: 400 }}>{r.productionDate || '—'}</span>
                      </div>
                    ))}
                    {prodHistory.length === 0 && <div className="idoc-hist-empty">Sin producciones registradas.</div>}
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
    </div>
  );
}

export function InventoryEntriesPage() { return <InventoryDocPage docType="IN" />; }
export function InventoryExitsPage() { return <InventoryDocPage docType="OUT" />; }
