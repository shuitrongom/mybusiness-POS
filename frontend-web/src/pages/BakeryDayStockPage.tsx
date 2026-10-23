import { useEffect, useMemo, useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Boxes, Search, Calendar, DollarSign, Layers, Trash2, ShoppingBag, PackageOpen, TrendingUp, ChevronDown } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp, staggerContainer, staggerItem } from '@/lib/motion';
import { useCashierBranch } from '@/lib/cashierBranch';
import { InfoBanner } from '@/components/InfoBanner';
import '@/pages/dashboard.css';
import '@/pages/inventory.css';
import '@/pages/inventory-doc.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const num = (n: number) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 3 }).format(n || 0);

// Avatar del artículo (color estable + iniciales), coherente con el resto de la app.
const AVATAR_COLORS = ['#2e6ef2', '#12b886', '#f59e0b', '#e11d48', '#7c5cff', '#0ea5e9', '#d97706', '#0891b2'];
function avatarColor(text: string): string {
  let hash = 0;
  for (let i = 0; i < text.length; i++) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}
function avatarInitials(name: string): string {
  const w = name.trim().split(/\s+/).filter(Boolean);
  if (w.length === 0) return '?';
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase();
}

interface Branch { id: number; name: string; active: boolean; }

/**
 * Fila de GET /bakery/stock/day: existencias del día por artículo de pan.
 * Todas las cifras cuadran con los movimientos reales (kardex inventory_movement +
 * bakery_waste_movement) y con la existencia real (inventory_stock):
 *  - produced     = producción de panadería del día (PURCHASE + ref 'PROD:')
 *  - sold/soldPos = venta real POS del día (SALE + ref 'SALE:')
 *  - otherExits   = salida normal del día (SALE + ref 'OUT-DOC:', con concepto)
 *  - wasteOut     = lo que pasó del stock normal a merma hoy (WASTE_OUT)
 *  - pasoMerma    = movimientos a merma del día (bakery_waste_movement, origin MANUAL)
 *  - reparto      = merma que salió a repartidor hoy (origin REPARTO)
 *  - waste/wasteBalance = SALDO de merma arrastrado (bakery_waste_stock), por sucursal
 *  - currentStock = existencia normal real; remaining = currentStock (existencia real)
 *  - initialStock = existencia inicial reconstruida; solo fiable HOY, null en días pasados
 */
interface DayRow {
  productId: number;
  productName: string;
  unit: string | null;
  price: number | null;
  produced: number;
  sold: number;
  soldPos: number;
  otherExits: number;
  wasteOut: number;
  pasoMerma: number;
  reparto: number;
  waste: number;
  wasteBalance: number;
  currentStock: number;
  remaining: number;
  value: number;
  initialStock: number | null;
  active: boolean;
}

/**
 * Colores de los chips del desglose de movimientos, coherentes con "Salidas recientes"
 * (InventoryDocPage): ámbar = pasó a merma, sky = reparto, neutro = salida normal.
 * Se añaden azul = producción y verde = venta POS, siguiendo la jerarquía de color del steering.
 */
const MV_COLOR = {
  produced: '#2e6ef2',   // azul: producción (entró stock)
  soldPos: '#12b886',    // verde: venta POS (ok)
  otherExits: '#64748b', // neutro: salida normal
  pasoMerma: '#d97706',  // ámbar: pasó a merma
  reparto: '#0ea5e9',    // sky: reparto de merma
  wasteBal: '#f59e0b',   // ámbar claro: saldo de merma arrastrado
} as const;

function todayIso(): string {
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Existencias del DÍA para panadería (una sola vista). Carga por defecto el día de hoy con la lista
 * completa de artículos de pan (sin exigir teclear); un buscador filtra en memoria y el calendario
 * permite ver días anteriores. Cada fila-tarjeta se expande para ver a dónde se fue cada pieza
 * (producción, venta POS, otras salidas, pasó a merma, reparto) con los mismos chips de color que
 * "Salidas recientes", sin descomponer la pantalla. "Restante" = existencia REAL; la merma se
 * arrastra entre días, por eso se muestra como saldo propio.
 */
export function BakeryDayStockPage() {
  const [branches, setBranches] = useState<Branch[]>([]);
  // Sucursal del cajero desde la FUENTE ÚNICA (turno abierto > última usada > primera activa),
  // para que Existencias del día lea el MISMO almacén que el POS y el resto de inventario.
  const { branchId, setBranchId } = useCashierBranch(branches);
  // Fecha vacía = "hoy" según el SERVIDOR (zona México). Al estar vacía NO se manda el parámetro
  // date y el backend resuelve el día con LocalDate.now(México), evitando el desfase del cliente.
  const [date, setDate] = useState<string>('');
  const [rows, setRows] = useState<DayRow[]>([]);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [expanded, setExpanded] = useState<number | null>(null);

  useEffect(() => {
    api.get<Branch[]>('/branches')
      .then((r) => {
        const active = r.data.filter((b) => b.active);
        setBranches(active);
        // La sucursal la resuelve useCashierBranch (fuente única) al cambiar esta lista.
      })
      .catch(() => toast.error('No se pudieron cargar las sucursales'));
  }, []);

  const load = (branch: number, day: string) => {
    if (!branch) return;
    setBusy(true);
    setExpanded(null);
    // El día "hoy" lo gobierna el SERVIDOR: si no hay fecha elegida, se omite el parámetro date
    // para que el backend use su propio LocalDate.now(México). Solo se envía al elegir un día.
    const params: Record<string, string | number> = { branchId: branch };
    if (day) params.date = day;
    api.get<DayRow[]>('/bakery/stock/day', { params })
      .then((r) => setRows(r.data))
      .catch(() => { setRows([]); toast.error('No se pudieron cargar las existencias del día'); })
      .finally(() => setBusy(false));
  };

  // Carga automática al tener sucursal y cuando cambia la fecha o la sucursal (sin teclear nada).
  useEffect(() => {
    if (branchId) load(branchId, date);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, date]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => r.productName.toLowerCase().includes(q) || String(r.productId) === q);
  }, [rows, query]);

  // Totales del día. Sumamos cada concepto por separado (no se mezclan peras con manzanas) y
  // derivamos el % vendido global y las unidades totales. "initial" solo suma filas con dato (hoy).
  const totals = useMemo(() => {
    const t = filtered.reduce(
      (acc, r) => ({
        initial: acc.initial + (r.initialStock ?? 0),
        hasInitial: acc.hasInitial || r.initialStock != null,
        produced: acc.produced + (r.produced || 0),
        soldPos: acc.soldPos + (r.soldPos || 0),
        otherExits: acc.otherExits + (r.otherExits || 0),
        waste: acc.waste + (r.waste || 0),
        remaining: acc.remaining + (r.remaining || 0),
        value: acc.value + (r.value || 0),
      }),
      { initial: 0, hasInitial: false, produced: 0, soldPos: 0, otherExits: 0, waste: 0, remaining: 0, value: 0 },
    );
    // Base del % vendido: lo que estuvo disponible para vender = inicial (si hay) + producido.
    const base = (t.hasInitial ? t.initial : 0) + t.produced;
    const soldPct = base > 0 ? Math.min(100, Math.round((t.soldPos / base) * 100)) : 0;
    return { ...t, soldPct };
  }, [filtered]);

  return (
    <div>
      <div className="inv-head">
        <div>
          <h1 className="page-title">Existencias del día</h1>
          <p className="page-sub">Producido, vendido, otras salidas, merma y lo que queda por artículo de pan</p>
        </div>
        {branches.length > 0 && (
          <select className="inv-branch-select" value={branchId}
            onChange={(e) => setBranchId(Number(e.target.value))}>
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        )}
      </div>

      <div style={{ marginTop: 'var(--space-3)' }}>
        <InfoBanner icon={<Boxes size={18} />} tone="brand" title="Existencias del día de panadería">
          Resumen del día por artículo: lo producido, lo vendido, otras salidas, lo que pasó a merma y
          lo que queda (existencia real). Abre una fila para ver a dónde se fue cada pieza. Usa el
          calendario para ver días anteriores; la merma se arrastra de un día a otro.
        </InfoBanner>
      </div>

      {/* KPIs del día — más información: existencia inicial, producido, vendido POS, otras salidas,
          merma (saldo), valor en existencia y % vendido global. */}
      <motion.div className="inv-kpis" variants={staggerContainer} initial="hidden" animate="visible">
        {totals.hasInitial && (
          <Kpi icon={<PackageOpen size={18} />} tint="violet" label="Existencia inicial" value={num(totals.initial)} />
        )}
        <Kpi icon={<Layers size={18} />} tint="blue" label="Producido" value={num(totals.produced)} />
        <Kpi icon={<ShoppingBag size={18} />} tint="green" label="Vendido (POS)" value={num(totals.soldPos)} />
        <Kpi icon={<TrendingUp size={18} />} tint="blue" label="% vendido global" value={`${totals.soldPct}%`} />
        <Kpi icon={<Boxes size={18} />} tint="violet" label="Otras salidas" value={num(totals.otherExits)} />
        <Kpi icon={<Trash2 size={18} />} tint="amber" label="En merma (saldo)" value={num(totals.waste)} />
        <Kpi icon={<DollarSign size={18} />} tint="green" label="Valor en existencia" value={money(totals.value)} />
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
        variants={fadeInUp} initial="hidden" animate="visible">
        <div className="inv-toolbar">
          <div className="inv-search">
            <Search size={16} />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar artículo de pan…" />
          </div>
          <label className="field" style={{ margin: 0 }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><Calendar size={14} /> Día</span>
            <input type="date" value={date || todayIso()} max={todayIso()} onChange={(e) => setDate(e.target.value)} />
          </label>
        </div>

        {(() => {
          // Grid COMPARTIDO por cabecera, filas y pie (alineación por construcción, sin parches).
          // Columna "Inicial" solo cuando hay dato (hoy); el pie usa el MISMO grid.
          const showInitial = totals.hasInitial;
          const cols = [
            'minmax(170px, 1fr)',            // Artículo
            ...(showInitial ? ['92px'] : []), // Inicial
            '92px',                           // Producido
            '120px',                          // Vendido (+%barra)
            '100px',                          // Otras salidas
            '104px',                          // En merma (saldo)
            '116px',                          // Restante (existencia real)
            '100px',                          // Precio
            '120px',                          // Valor
          ].join(' ');

          return (
            <div className="iday">
              <div className="iday-head" style={{ gridTemplateColumns: cols }}>
                <span>Artículo</span>
                {showInitial && <span className="iday-right">Inicial</span>}
                <span className="iday-right">Producido</span>
                <span className="iday-right">Vendido</span>
                <span className="iday-right">Otras salidas</span>
                <span className="iday-right">En merma</span>
                <span className="iday-right">Restante</span>
                <span className="iday-right">Precio</span>
                <span className="iday-right">Valor</span>
              </div>
              <motion.div className="iday-body" variants={staggerContainer} initial="hidden" animate="visible">
                {filtered.map((r) => {
                  // % vendido del artículo sobre lo disponible (inicial + producido, o producido).
                  const base = (r.initialStock ?? 0) + (r.produced || 0);
                  const pct = base > 0 ? Math.min(100, Math.round((r.soldPos / base) * 100)) : 0;
                  const isOpen = expanded === r.productId;
                  const hasMovements = (r.produced || r.soldPos || r.otherExits || r.pasoMerma
                    || r.reparto || r.wasteOut || r.wasteBalance) > 0;
                  return (
                    <div className="iday-card" key={r.productId}>
                      <motion.div
                        className={`iday-row ${isOpen ? 'is-open' : ''}`}
                        style={{ gridTemplateColumns: cols }}
                        variants={staggerItem}
                        role="button"
                        tabIndex={0}
                        onClick={() => setExpanded(isOpen ? null : r.productId)}
                        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setExpanded(isOpen ? null : r.productId); } }}
                      >
                        <div className="iday-article">
                          <span className="iday-avatar" style={{ background: avatarColor(r.productName) }}>
                            {avatarInitials(r.productName)}
                          </span>
                          <div style={{ minWidth: 0 }}>
                            <div className="iday-name">
                              <span>{r.productName}</span>
                              {!r.active && <span className="iday-badge-off">Inactivo</span>}
                              {!r.active && r.remaining === 0 && r.wasteBalance > 0 && (
                                <span className="iday-waste-chip" title="Visible por saldo de merma">
                                  merma {num(r.wasteBalance)}
                                </span>
                              )}
                              <ChevronDown size={14} className={`iday-chevron ${isOpen ? 'is-open' : ''}`} />
                            </div>
                            <div className="iday-unit">{r.unit || 'pieza'}</div>
                          </div>
                        </div>
                        {showInitial && (
                          <span className={`iday-num ${(r.initialStock ?? 0) > 0 ? '' : 'is-dim'}`}>
                            {r.initialStock == null ? '—' : num(r.initialStock)}
                          </span>
                        )}
                        <span className={`iday-num ${r.produced > 0 ? '' : 'is-dim'}`}>{num(r.produced)}</span>
                        <div className="iday-sold">
                          <div className="iday-sold-top">
                            <span className="iday-sold-qty">{num(r.soldPos)}</span>
                            {base > 0 && <span className="iday-sold-pct">{pct}%</span>}
                          </div>
                          <div className="iday-bar"><div className="iday-bar-fill" style={{ width: `${pct}%` }} /></div>
                        </div>
                        <span className={`iday-num ${r.otherExits > 0 ? '' : 'is-dim'}`}>{num(r.otherExits)}</span>
                        <span className={`iday-waste ${r.waste > 0 ? '' : 'is-zero'}`}>{num(r.waste)}</span>
                        <span className={`iday-remaining ${r.remaining > 0 ? '' : 'is-zero'}`}>{num(r.remaining)}</span>
                        <span className="iday-price">{money(r.price || 0)}</span>
                        <span className="iday-value">{money(r.value)}</span>
                      </motion.div>

                      <AnimatePresence initial={false}>
                        {isOpen && (
                          <motion.div
                            className="iday-detail"
                            initial={{ height: 0, opacity: 0 }}
                            animate={{ height: 'auto', opacity: 1 }}
                            exit={{ height: 0, opacity: 0 }}
                            transition={{ duration: 0.18 }}
                          >
                            <div className="iday-detail-inner">
                              <span className="iday-detail-label">Movimientos del día</span>
                              {hasMovements ? (
                                <div className="iday-mv-chips">
                                  {r.produced > 0 && <MvChip color={MV_COLOR.produced} label="Producción" value={r.produced} />}
                                  {r.soldPos > 0 && <MvChip color={MV_COLOR.soldPos} label="Vendido POS" value={r.soldPos} />}
                                  {r.otherExits > 0 && <MvChip color={MV_COLOR.otherExits} label="Otras salidas" value={r.otherExits} />}
                                  {r.pasoMerma > 0 && <MvChip color={MV_COLOR.pasoMerma} label="Pasó a merma" value={r.pasoMerma} />}
                                  {r.reparto > 0 && <MvChip color={MV_COLOR.reparto} label="Reparto de merma" value={r.reparto} />}
                                  {r.wasteBalance > 0 && <MvChip color={MV_COLOR.wasteBal} label="Saldo en merma" value={r.wasteBalance} />}
                                </div>
                              ) : (
                                <span className="iday-detail-empty">Sin movimientos este día (solo existencia).</span>
                              )}
                            </div>
                          </motion.div>
                        )}
                      </AnimatePresence>
                    </div>
                  );
                })}
                {filtered.length === 0 && (
                  <div className="iday-empty">
                    {busy ? 'Cargando…'
                      : rows.length === 0 ? 'Sin producción, ventas ni existencia este día. Registra la producción en Inventario → Entradas.'
                        : 'Ningún artículo coincide con la búsqueda.'}
                  </div>
                )}
              </motion.div>

              {filtered.length > 0 && (
                <div className="iday-row iday-foot" style={{ gridTemplateColumns: cols }}>
                  <span className="iday-foot-label">Totales ({filtered.length} artículos)</span>
                  {showInitial && <span className="iday-num">{num(totals.initial)}</span>}
                  <span className="iday-num">{num(totals.produced)}</span>
                  <span className="iday-num">{num(totals.soldPos)}</span>
                  <span className="iday-num">{num(totals.otherExits)}</span>
                  <span className="iday-num">{num(totals.waste)}</span>
                  <span className="iday-num">{num(totals.remaining)}</span>
                  <span />
                  <span className="iday-value">{money(totals.value)}</span>
                </div>
              )}
            </div>
          );
        })()}
      </motion.div>
    </div>
  );
}

function MvChip({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <span className="iday-mv-chip" style={{
      background: `color-mix(in srgb, ${color} 16%, transparent)`,
      color,
      borderColor: `color-mix(in srgb, ${color} 32%, transparent)`,
    }}>
      {label}<strong>{num(value)}</strong>
    </span>
  );
}

function Kpi({ icon, label, value, tint }: { icon: React.ReactNode; label: string; value: string; tint: string }) {
  return (
    <motion.div className="kpi-card" variants={staggerItem}>
      <span className={`kpi-icon kpi-${tint}`}>{icon}</span>
      <div className="kpi-body">
        <span className="kpi-label">{label}</span>
        <span className="kpi-value">{value}</span>
      </div>
    </motion.div>
  );
}
