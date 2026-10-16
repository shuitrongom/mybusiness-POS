import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Boxes, Search, Calendar } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

interface Branch { id: number; name: string; active: boolean; }
interface BakeryItem { id: number; sku: string | null; name: string; unit: string | null; price: number | null; cost: number | null; }

/** Respuesta de GET /bakery/stock/daily. */
interface DailyStock {
  date: string;
  producido: number;
  vendido: number;
  restanteDelDia: number;
  existenciaActual?: number;
}
/** Un punto de la serie diaria de GET /bakery/stock/range (groupBy=day). */
interface RangePoint { day: string; produced: number; sold: number; remaining: number; }
/** Respuesta de GET /bakery/stock/range. */
interface RangeStock {
  from: string;
  to: string;
  producido: number;
  vendido: number;
  restanteDelRango: number;
  serie?: RangePoint[];
  existenciaActual?: number;
}

function todayIso(): string {
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Existencias de panadería por DÍA o RANGO para un artículo de pan. Buscador PREMIUM (RF14): filtra
 * en memoria sobre los artículos ya cargados y, para catálogos grandes, consulta /items/search. La
 * tabla muestra producido / vendido / restante; en rango con agrupación diaria, el detalle por día.
 */
export function BakeryStockPage() {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [branchId, setBranchId] = useState<number>(0);
  const [items, setItems] = useState<BakeryItem[]>([]);
  const [query, setQuery] = useState('');
  const [remote, setRemote] = useState<BakeryItem[]>([]);
  const [product, setProduct] = useState<BakeryItem | null>(null);

  const [mode, setMode] = useState<'daily' | 'range'>('daily');
  const [date, setDate] = useState<string>(todayIso());
  const [from, setFrom] = useState<string>(todayIso());
  const [to, setTo] = useState<string>(todayIso());

  const [daily, setDaily] = useState<DailyStock | null>(null);
  const [range, setRange] = useState<RangeStock | null>(null);
  const [busy, setBusy] = useState(false);

  const loadItems = (branch: number) => {
    api.get<BakeryItem[]>('/bakery/items', { params: { branchId: branch || undefined } })
      .then((r) => setItems(r.data)).catch(() => setItems([]));
  };

  useEffect(() => {
    api.get<Branch[]>('/branches')
      .then((r) => {
        const active = r.data.filter((b) => b.active);
        setBranches(active);
        const first = active[0]?.id ?? 0;
        setBranchId(first);
        loadItems(first);
      })
      .catch(() => toast.error('No se pudieron cargar las sucursales'));
  }, []);

  // Filtro en memoria sobre los artículos cargados (buscador premium, respuesta instantánea).
  const localMatches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return items.filter((p) => p.name.toLowerCase().includes(q)
      || (p.sku ?? '').toLowerCase().includes(q)
      || String(p.id) === q).slice(0, 12);
  }, [items, query]);

  // Respaldo remoto (/items/search) para catálogos grandes: solo cuando el filtro en memoria no
  // encuentra coincidencias pero sí hay texto. Evita "no encuentro" en catálogos enormes.
  useEffect(() => {
    const q = query.trim();
    if (!q || localMatches.length > 0) { setRemote([]); return; }
    let cancelled = false;
    const t = setTimeout(() => {
      api.get<BakeryItem[]>('/bakery/items/search', { params: { q, branchId: branchId || undefined } })
        .then((r) => { if (!cancelled) setRemote(r.data); })
        .catch(() => { if (!cancelled) setRemote([]); });
    }, 220);
    return () => { cancelled = true; clearTimeout(t); };
  }, [query, localMatches.length, branchId]);

  const suggestions = localMatches.length > 0 ? localMatches : remote;

  const consult = async () => {
    if (!product) { toast.info('Elige un artículo de pan'); return; }
    if (!branchId) { toast.info('Selecciona una sucursal'); return; }
    setBusy(true);
    try {
      if (mode === 'daily') {
        const r = await api.get<DailyStock>('/bakery/stock/daily', {
          params: { productId: product.id, branchId, date },
        });
        setDaily(r.data);
        setRange(null);
      } else {
        const r = await api.get<RangeStock>('/bakery/stock/range', {
          params: { productId: product.id, branchId, from, to, groupBy: 'day' },
        });
        setRange(r.data);
        setDaily(null);
      }
    } catch {
      toast.error('No se pudo consultar la existencia');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1 className="page-title">Existencias de panadería</h1>
      <p className="page-sub">Producido, vendido y restante por día o por rango para cada artículo de pan</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="pur-head">
          <label className="field"><span>Sucursal</span>
            <select value={branchId} onChange={(e) => { const v = Number(e.target.value); setBranchId(v); loadItems(v); }}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select></label>
          <label className="field"><span>Modo</span>
            <select value={mode} onChange={(e) => setMode(e.target.value as 'daily' | 'range')}>
              <option value="daily">Por día</option>
              <option value="range">Por rango</option>
            </select></label>
          {mode === 'daily' ? (
            <label className="field"><span>Fecha</span>
              <input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
          ) : (
            <>
              <label className="field"><span>Desde</span>
                <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
              <label className="field"><span>Hasta</span>
                <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
            </>
          )}
        </div>

        <div className="pur-add" style={{ marginTop: 'var(--space-4)' }}>
          <div className="cust-search" style={{ position: 'relative' }}>
            <Search size={16} />
            <input
              value={product ? product.name : query}
              onChange={(e) => { setProduct(null); setQuery(e.target.value); }}
              placeholder="Buscar artículo de pan…"
            />
            {!product && suggestions.length > 0 && (
              <div className="pur-suggest">
                {suggestions.map((p) => (
                  <button key={p.id} onClick={() => { setProduct(p); setQuery(''); }}>
                    <span>{p.name}</span><span className="muted">{p.unit || 'pz'}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
          <button className="btn-accent" disabled={busy} onClick={consult} style={{ marginLeft: 'var(--space-3)' }}>
            <Calendar size={16} /> Consultar
          </button>
        </div>
      </motion.div>

      {daily && (
        <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Boxes size={18} /> {product?.name} · {daily.date}</h3>
          <table className="ret-table">
            <thead><tr>
              <th>Concepto</th><th className="ta-right">Producido</th><th className="ta-right">Vendido</th>
              <th className="ta-right">Restante del día</th>
              {daily.existenciaActual != null && <th className="ta-right">Existencia actual</th>}
            </tr></thead>
            <tbody>
              <tr>
                <td><strong>{daily.date}</strong></td>
                <td className="ta-right">{int(daily.producido)}</td>
                <td className="ta-right">{int(daily.vendido)}</td>
                <td className="ta-right"><strong>{int(daily.restanteDelDia)}</strong></td>
                {daily.existenciaActual != null && <td className="ta-right">{int(daily.existenciaActual)}</td>}
              </tr>
            </tbody>
          </table>
        </motion.div>
      )}

      {range && (
        <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Boxes size={18} /> {product?.name} · {range.from} → {range.to}</h3>
          <table className="ret-table">
            <thead><tr>
              <th>Día</th><th className="ta-right">Producido</th><th className="ta-right">Vendido</th><th className="ta-right">Restante</th>
            </tr></thead>
            <tbody>
              {(range.serie ?? []).map((p) => (
                <tr key={p.day}>
                  <td>{p.day}</td>
                  <td className="ta-right">{int(p.produced)}</td>
                  <td className="ta-right">{int(p.sold)}</td>
                  <td className="ta-right">{int(p.remaining)}</td>
                </tr>
              ))}
              {(range.serie == null || range.serie.length === 0) && (
                <tr><td colSpan={4} className="ret-empty">Sin movimientos en el rango.</td></tr>
              )}
              <tr>
                <td><strong>Total</strong></td>
                <td className="ta-right"><strong>{int(range.producido)}</strong></td>
                <td className="ta-right"><strong>{int(range.vendido)}</strong></td>
                <td className="ta-right"><strong>{int(range.restanteDelRango)}</strong></td>
              </tr>
            </tbody>
          </table>
          {range.existenciaActual != null && (
            <p className="page-sub" style={{ marginTop: 'var(--space-2)' }}>
              Existencia actual: <strong>{int(range.existenciaActual)}</strong>
            </p>
          )}
        </motion.div>
      )}
    </div>
  );
}
