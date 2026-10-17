import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Boxes, Search, Calendar, DollarSign, Layers, Trash2 } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp, staggerContainer, staggerItem } from '@/lib/motion';
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

/** Fila de GET /bakery/stock/day: existencias del día por artículo de pan. */
interface DayRow {
  productId: number;
  productName: string;
  unit: string | null;
  price: number | null;
  produced: number;
  sold: number;
  waste: number;
  currentStock: number;
  remaining: number;
  value: number;
}

function todayIso(): string {
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Existencias del DÍA para panadería (una sola vista). Carga por defecto el día de hoy con la lista
 * completa de artículos de pan (sin exigir teclear); un buscador filtra en memoria y el calendario
 * permite ver días anteriores. Columnas: producido, vendido, EN MERMA, restante del día y valor.
 * La existencia de merma se arrastra entre días, por eso se muestra como columna propia.
 */
export function BakeryDayStockPage() {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [branchId, setBranchId] = useState<number>(0);
  const [date, setDate] = useState<string>(todayIso());
  const [rows, setRows] = useState<DayRow[]>([]);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<Branch[]>('/branches')
      .then((r) => {
        const active = r.data.filter((b) => b.active);
        setBranches(active);
        setBranchId(active[0]?.id ?? 0);
      })
      .catch(() => toast.error('No se pudieron cargar las sucursales'));
  }, []);

  const load = (branch: number, day: string) => {
    if (!branch) return;
    setBusy(true);
    api.get<DayRow[]>('/bakery/stock/day', { params: { branchId: branch, date: day } })
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

  const totals = useMemo(() => filtered.reduce(
    (acc, r) => ({
      produced: acc.produced + (r.produced || 0),
      sold: acc.sold + (r.sold || 0),
      waste: acc.waste + (r.waste || 0),
      value: acc.value + (r.value || 0),
    }),
    { produced: 0, sold: 0, waste: 0, value: 0 },
  ), [filtered]);

  return (
    <div>
      <div className="inv-head">
        <div>
          <h1 className="page-title">Existencias del día</h1>
          <p className="page-sub">Producido, vendido, en merma y restante por artículo de pan</p>
        </div>
        {branches.length > 0 && (
          <select className="inv-branch-select" value={branchId}
            onChange={(e) => setBranchId(Number(e.target.value))}>
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        )}
      </div>

      {/* KPIs del día */}
      <motion.div className="inv-kpis" variants={staggerContainer} initial="hidden" animate="visible">
        <Kpi icon={<Layers size={18} />} tint="blue" label="Producido" value={num(totals.produced)} />
        <Kpi icon={<Boxes size={18} />} tint="green" label="Vendido (normal)" value={num(totals.sold)} />
        <Kpi icon={<Trash2 size={18} />} tint="amber" label="En merma" value={num(totals.waste)} />
        <Kpi icon={<DollarSign size={18} />} tint="violet" label="Valor en existencia" value={money(totals.value)} />
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
            <input type="date" value={date} max={todayIso()} onChange={(e) => setDate(e.target.value)} />
          </label>
        </div>

        <div className="iday">
          <div className="iday-head">
            <span>Artículo</span>
            <span className="iday-right">Producido</span>
            <span className="iday-right">Vendido</span>
            <span className="iday-right">En merma</span>
            <span className="iday-right">Restante del día</span>
            <span className="iday-right">Valor</span>
          </div>
          <motion.div className="iday-body" variants={staggerContainer} initial="hidden" animate="visible">
            {filtered.map((r) => (
              <motion.div className="iday-row" key={r.productId} variants={staggerItem}>
                <div className="iday-article">
                  <span className="iday-avatar" style={{ background: avatarColor(r.productName) }}>
                    {avatarInitials(r.productName)}
                  </span>
                  <div style={{ minWidth: 0 }}>
                    <div className="iday-name">{r.productName}</div>
                    <div className="iday-unit">{r.unit || 'pieza'}</div>
                  </div>
                </div>
                <span className="iday-num">{num(r.produced)}</span>
                <span className="iday-num is-dim">{num(r.sold)}</span>
                <span className={`iday-waste ${r.waste > 0 ? '' : 'is-zero'}`}>{num(r.waste)}</span>
                <span className={`iday-remaining ${r.remaining > 0 ? '' : 'is-zero'}`}>{num(r.remaining)}</span>
                <span className="iday-value">{money(r.value)}</span>
              </motion.div>
            ))}
            {filtered.length === 0 && (
              <div className="iday-empty">
                {busy ? 'Cargando…'
                  : rows.length === 0 ? 'Sin producción, ventas ni existencia este día. Registra la producción en Inventario → Entradas.'
                    : 'Ningún artículo coincide con la búsqueda.'}
              </div>
            )}
          </motion.div>
        </div>
      </motion.div>
    </div>
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
