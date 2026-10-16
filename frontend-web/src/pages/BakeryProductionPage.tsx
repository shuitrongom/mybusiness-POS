import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Croissant, Plus, Trash2, Check, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Branch { id: number; name: string; active: boolean; }
interface EntryStatus { id: number; code: string; label: string; sortOrder: number; active: boolean; }
interface BakeryItem { id: number; sku: string | null; name: string; unit: string | null; price: number | null; cost: number | null; }
interface Line {
  productId: number;
  description: string;
  quantity: number;
  unit: string;
  entryPrice: number;
  unitCost: number;
}
/** Fila por renglón de GET /bakery/productions (entradas recientes: folio + artículo). */
interface ProductionRow {
  productionId: number;
  folio: string | null;
  productName: string | null;
  description: string | null;
  quantity: number | null;
  unit: string | null;
  entryPrice: number | null;
  unitCost: number | null;
  lineTotal: number | null;
  productionDate: string | null;
}

/**
 * Captura de PRODUCCIÓN de panadería: elige sucursal y estatus de entrada, busca artículos de pan
 * y arma los renglones (artículo, cantidad, unidad, precio de entrada, costo, importe). Muestra la
 * tabla descriptiva del documento (RF15) y las entradas recientes con folio y artículo (RF4). Al
 * finalizar afecta las existencias del día (POST /bakery/productions).
 */
export function BakeryProductionPage() {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [statuses, setStatuses] = useState<EntryStatus[]>([]);
  const [branchId, setBranchId] = useState<number>(0);
  const [statusCode, setStatusCode] = useState<string>('PRODUCED');
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [pick, setPick] = useState('');
  const [results, setResults] = useState<BakeryItem[]>([]);
  const [recent, setRecent] = useState<ProductionRow[]>([]);
  const [busy, setBusy] = useState(false);

  const loadRecent = (branch: number) => {
    api.get<ProductionRow[]>('/bakery/productions', { params: { limit: 15, branchId: branch || undefined } })
      .then((r) => setRecent(r.data)).catch(() => setRecent([]));
  };

  useEffect(() => {
    (async () => {
      try {
        const [br, st] = await Promise.all([
          api.get<Branch[]>('/branches'),
          api.get<EntryStatus[]>('/bakery/entry-statuses'),
        ]);
        const active = br.data.filter((b) => b.active);
        setBranches(active);
        const firstBranch = active[0]?.id ?? 0;
        setBranchId(firstBranch);
        setStatuses(st.data.filter((s) => s.active));
        loadRecent(firstBranch);
      } catch {
        toast.error('No se pudo cargar el catálogo de panadería');
      }
    })();
  }, []);

  // Buscador de artículos de pan contra /items/search (q vacío => el backend devuelve lista vacía).
  useEffect(() => {
    const q = pick.trim();
    if (!q) { setResults([]); return; }
    let cancelled = false;
    const t = setTimeout(() => {
      api.get<BakeryItem[]>('/bakery/items/search', { params: { q, branchId: branchId || undefined } })
        .then((r) => { if (!cancelled) setResults(r.data); })
        .catch(() => { if (!cancelled) setResults([]); });
    }, 200);
    return () => { cancelled = true; clearTimeout(t); };
  }, [pick, branchId]);

  const add = (p: BakeryItem) => {
    setLines((prev) => prev.some((l) => l.productId === p.id) ? prev
      : [...prev, {
        productId: p.id,
        description: p.name,
        quantity: 1,
        unit: p.unit || 'pz',
        entryPrice: p.price || 0,
        unitCost: p.cost || 0,
      }]);
    setPick('');
    setResults([]);
  };
  const setLine = (i: number, patch: Partial<Line>) =>
    setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const removeLine = (i: number) => setLines((prev) => prev.filter((_, idx) => idx !== i));

  const totalUnits = useMemo(() => lines.reduce((s, l) => s + l.quantity, 0), [lines]);
  const totalValue = useMemo(() => lines.reduce((s, l) => s + l.quantity * l.entryPrice, 0), [lines]);

  const submit = async () => {
    if (!branchId) { toast.info('Selecciona una sucursal'); return; }
    if (lines.length === 0) { toast.info('Agrega al menos un artículo de pan'); return; }
    setBusy(true);
    try {
      await api.post('/bakery/productions', {
        branchId,
        statusCode: statusCode || null,
        notes: notes || null,
        lines: lines.map((l) => ({
          productId: l.productId,
          description: l.description,
          quantity: l.quantity,
          unit: l.unit || null,
          entryPrice: l.entryPrice,
          unitCost: l.unitCost,
        })),
      });
      toast.success('Producción registrada', 'Se actualizaron las existencias del día.');
      setLines([]); setNotes('');
      loadRecent(branchId);
    } catch {
      toast.error('No se pudo registrar la producción');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1 className="page-title">Captura de producción de pan</h1>
      <p className="page-sub">Registra el pan producido del día con su precio de entrada y costo</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="pur-head">
          <label className="field"><span>Sucursal</span>
            <select value={branchId} onChange={(e) => { const v = Number(e.target.value); setBranchId(v); loadRecent(v); }}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select></label>
          <label className="field"><span>Estatus de entrada</span>
            <select value={statusCode} onChange={(e) => setStatusCode(e.target.value)}>
              {statuses.length === 0 && <option value="PRODUCED">Producción</option>}
              {statuses.map((s) => <option key={s.id} value={s.code}>{s.label}</option>)}
            </select></label>
          <label className="field" style={{ gridColumn: 'span 2' }}><span>Observaciones</span>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
        </div>

        <div className="pur-add" style={{ marginTop: 'var(--space-4)' }}>
          <div className="cust-search" style={{ position: 'relative' }}>
            <Search size={16} />
            <input value={pick} onChange={(e) => setPick(e.target.value)} placeholder="Buscar artículo de pan para agregar…" />
            {results.length > 0 && (
              <div className="pur-suggest">
                {results.map((p) => (
                  <button key={p.id} onClick={() => add(p)}>
                    <span>{p.name}</span><span className="muted">{money(p.price || 0)}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Tabla descriptiva del documento (RF15): artículo, cantidad, unidad, precio, costo, importe. */}
        <table className="pur-table">
          <thead><tr>
            <th>Artículo</th><th className="ta-right">Cantidad</th><th>Unidad</th>
            <th className="ta-right">Precio de entrada</th><th className="ta-right">Costo</th>
            <th className="ta-right">Importe</th><th></th>
          </tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={l.productId}>
                <td>{l.description}</td>
                <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.001" value={l.quantity} onChange={(e) => setLine(i, { quantity: Number(e.target.value) || 0 })} /></td>
                <td><input className="pur-in" value={l.unit} onChange={(e) => setLine(i, { unit: e.target.value })} style={{ width: '5rem' }} /></td>
                <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.01" value={l.entryPrice} onChange={(e) => setLine(i, { entryPrice: Number(e.target.value) || 0 })} /></td>
                <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.01" value={l.unitCost} onChange={(e) => setLine(i, { unitCost: Number(e.target.value) || 0 })} /></td>
                <td className="ta-right">{money(l.quantity * l.entryPrice)}</td>
                <td className="ta-right"><button className="icon-btn icon-btn-danger" onClick={() => removeLine(i)} aria-label="Quitar"><Trash2 size={14} /></button></td>
              </tr>
            ))}
            {lines.length === 0 && <tr><td colSpan={7} className="ret-empty"><Plus size={18} /> Agrega artículos de pan al documento.</td></tr>}
          </tbody>
        </table>

        <div className="pur-foot">
          <div />
          <div className="pur-totals">
            <div className="pur-total-row"><span>Piezas</span><span>{totalUnits}</span></div>
            <div className="pur-total-row pur-total-grand"><span>Valor de entrada</span><strong>{money(totalValue)}</strong></div>
            <div className="pur-actions">
              <button className="btn-accent" disabled={busy} onClick={submit}>
                <Check size={16} /> Registrar producción
              </button>
            </div>
          </div>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Croissant size={18} /> Entradas recientes</h3>
        <table className="ret-table">
          <thead><tr>
            <th>Folio</th><th>Artículo</th><th className="ta-right">Cantidad</th><th>Unidad</th>
            <th className="ta-right">Precio</th><th className="ta-right">Importe</th><th>Fecha</th>
          </tr></thead>
          <tbody>
            {recent.map((r, i) => (
              <tr key={`${r.productionId}-${i}`}>
                <td><strong>{r.folio || '—'}</strong></td>
                <td>{r.productName || r.description || <span className="muted">—</span>}</td>
                <td className="ta-right">{r.quantity ?? 0}</td>
                <td>{r.unit || '—'}</td>
                <td className="ta-right">{money(r.entryPrice || 0)}</td>
                <td className="ta-right">{money(r.lineTotal || 0)}</td>
                <td>{r.productionDate || '—'}</td>
              </tr>
            ))}
            {recent.length === 0 && <tr><td colSpan={7} className="ret-empty">Sin producciones registradas.</td></tr>}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}
