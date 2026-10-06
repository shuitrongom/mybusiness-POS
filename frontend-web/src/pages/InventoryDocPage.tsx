import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { ArrowDownToLine, ArrowUpFromLine, Plus, Trash2, Check, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Concept { id: number; name: string; direction: string; }
interface Product { id: number; name: string; cost: number; unit: string; }
interface Branch { id: number; name: string; active: boolean; }
interface Line { productId: number; description: string; quantity: number; unitCost: number; unit: string; }
interface DocRow {
  id: number; folio: string; conceptName: string | null; branchName: string | null;
  totalQty: number; totalValue: number; createdBy: string | null; createdAt: string;
}

/**
 * Documento de ENTRADA o SALIDA de inventario. Reutilizable: recibe el tipo (IN/OUT). Elige
 * concepto (motivo), almacén y captura renglones. Al finalizar afecta las existencias.
 */
export function InventoryDocPage({ docType }: { docType: 'IN' | 'OUT' }) {
  const isIn = docType === 'IN';
  const [concepts, setConcepts] = useState<Concept[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [conceptId, setConceptId] = useState<number>(0);
  const [branchId, setBranchId] = useState<number>(0);
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [pick, setPick] = useState('');
  const [history, setHistory] = useState<DocRow[]>([]);
  const [busy, setBusy] = useState(false);

  const loadHistory = () => {
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

  const filtered = useMemo(() => {
    const q = pick.trim().toLowerCase();
    if (!q) return [];
    return products.filter((p) => p.name.toLowerCase().includes(q) || String(p.id) === q).slice(0, 8);
  }, [products, pick]);

  const add = (p: Product) => {
    setLines((prev) => prev.some((l) => l.productId === p.id) ? prev
      : [...prev, { productId: p.id, description: p.name, quantity: 1, unitCost: p.cost || 0, unit: p.unit }]);
    setPick('');
  };
  const setLine = (i: number, patch: Partial<Line>) => setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const removeLine = (i: number) => setLines((prev) => prev.filter((_, idx) => idx !== i));

  const totalUnits = lines.reduce((s, l) => s + l.quantity, 0);
  const totalValue = lines.reduce((s, l) => s + l.quantity * l.unitCost, 0);

  const submit = async () => {
    if (lines.length === 0) { toast.info('Agrega al menos un producto'); return; }
    setBusy(true);
    try {
      await api.post('/inventory/documents', {
        docType, conceptId: conceptId || null, branchId, notes: notes || null,
        lines: lines.map((l) => ({ productId: l.productId, description: l.description, quantity: l.quantity, unitCost: l.unitCost })),
      });
      toast.success(isIn ? 'Entrada registrada' : 'Salida registrada', 'Se actualizó el inventario.');
      setLines([]); setNotes('');
      loadHistory();
    } catch { toast.error('No se pudo registrar el movimiento'); } finally { setBusy(false); }
  };

  const Icon = isIn ? ArrowDownToLine : ArrowUpFromLine;

  return (
    <div>
      <h1 className="page-title">{isIn ? 'Entradas al inventario' : 'Salidas al inventario'}</h1>
      <p className="page-sub">{isIn ? 'Registra entradas de mercancía con su concepto' : 'Registra salidas de mercancía con su concepto'}</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="pur-head">
          <label className="field"><span>Concepto</span>
            <select value={conceptId} onChange={(e) => setConceptId(Number(e.target.value))}>
              <option value={0}>Selecciona…</option>
              {concepts.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select></label>
          <label className="field"><span>Almacén / sucursal</span>
            <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select></label>
          <label className="field" style={{ gridColumn: 'span 2' }}><span>Observaciones</span>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
        </div>

        <div className="pur-add" style={{ marginTop: 'var(--space-4)' }}>
          <div className="cust-search" style={{ position: 'relative' }}>
            <Search size={16} />
            <input value={pick} onChange={(e) => setPick(e.target.value)} placeholder="Buscar producto para agregar…" />
            {filtered.length > 0 && (
              <div className="pur-suggest">
                {filtered.map((p) => <button key={p.id} onClick={() => add(p)}><span>{p.name}</span><span className="muted">{money(p.cost)}</span></button>)}
              </div>
            )}
          </div>
        </div>

        <table className="pur-table">
          <thead><tr><th>Artículo</th><th className="ta-right">Cantidad</th><th>Unidad</th><th className="ta-right">Costo</th><th className="ta-right">Importe</th><th></th></tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={l.productId}>
                <td>{l.description}</td>
                <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.001" value={l.quantity} onChange={(e) => setLine(i, { quantity: Number(e.target.value) || 0 })} /></td>
                <td>{l.unit}</td>
                <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.01" value={l.unitCost} onChange={(e) => setLine(i, { unitCost: Number(e.target.value) || 0 })} /></td>
                <td className="ta-right">{money(l.quantity * l.unitCost)}</td>
                <td className="ta-right"><button className="icon-btn icon-btn-danger" onClick={() => removeLine(i)} aria-label="Quitar"><Trash2 size={14} /></button></td>
              </tr>
            ))}
            {lines.length === 0 && <tr><td colSpan={6} className="ret-empty"><Plus size={18} /> Agrega productos al documento.</td></tr>}
          </tbody>
        </table>

        <div className="pur-foot">
          <div />
          <div className="pur-totals">
            <div className="pur-total-row"><span>Unidades</span><span>{totalUnits}</span></div>
            <div className="pur-total-row pur-total-grand"><span>Valor</span><strong>{money(totalValue)}</strong></div>
            <div className="pur-actions">
              <button className="btn-accent" disabled={busy} onClick={submit}>
                <Check size={16} /> {isIn ? 'Registrar entrada' : 'Registrar salida'}
              </button>
            </div>
          </div>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Icon size={18} /> {isIn ? 'Entradas' : 'Salidas'} recientes</h3>
        <table className="ret-table">
          <thead><tr><th>Folio</th><th>Concepto</th><th>Almacén</th><th className="ta-right">Unidades</th><th className="ta-right">Valor</th><th>Registró</th></tr></thead>
          <tbody>
            {history.map((d) => (
              <tr key={d.id}>
                <td><strong>{d.folio}</strong></td>
                <td>{d.conceptName || <span className="muted">—</span>}</td>
                <td>{d.branchName || '—'}</td>
                <td className="ta-right">{d.totalQty}</td>
                <td className="ta-right">{money(d.totalValue)}</td>
                <td>{d.createdBy || '—'}</td>
              </tr>
            ))}
            {history.length === 0 && <tr><td colSpan={6} className="ret-empty">Sin movimientos registrados.</td></tr>}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}

export function InventoryEntriesPage() { return <InventoryDocPage docType="IN" />; }
export function InventoryExitsPage() { return <InventoryDocPage docType="OUT" />; }
