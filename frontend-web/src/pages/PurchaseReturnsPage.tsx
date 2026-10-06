import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Undo2, Plus, Trash2, Check, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/customers.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Supplier { id: number; name: string; }
interface Product { id: number; name: string; cost: number; }
interface Branch { id: number; name: string; active: boolean; }
interface Line { productId: number; description: string; quantity: number; unitCost: number; }
interface ReturnRow {
  id: number; folio: string; supplierName: string | null; total: number;
  reason: string | null; processedBy: string | null; createdAt: string;
}

/**
 * Devolución de compras: simple pero conectado. Elige proveedor y almacén, agrega productos a
 * devolver (da salida al inventario) y registra el documento. Puede ligarse a una compra.
 */
export function PurchaseReturnsPage() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [supplierId, setSupplierId] = useState<number>(0);
  const [branchId, setBranchId] = useState<number>(0);
  const [purchaseId, setPurchaseId] = useState('');
  const [reason, setReason] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [pick, setPick] = useState('');
  const [history, setHistory] = useState<ReturnRow[]>([]);
  const [busy, setBusy] = useState(false);

  const loadHistory = () => {
    api.get<ReturnRow[]>('/purchasing/returns', { params: { limit: 20 } })
      .then((r) => setHistory(r.data)).catch(() => setHistory([]));
  };

  useEffect(() => {
    (async () => {
      try {
        const [sup, prod, br] = await Promise.all([
          api.get<Supplier[]>('/purchasing/suppliers'),
          api.get<Product[]>('/catalog/products'),
          api.get<Branch[]>('/branches'),
        ]);
        setSuppliers(sup.data); setProducts(prod.data);
        const active = br.data.filter((b) => b.active);
        setBranches(active); if (active[0]) setBranchId(active[0].id);
      } catch { toast.error('No se pudo cargar el catálogo'); }
    })();
    loadHistory();
  }, []);

  const filtered = useMemo(() => {
    const q = pick.trim().toLowerCase();
    if (!q) return [];
    return products.filter((p) => p.name.toLowerCase().includes(q) || String(p.id) === q).slice(0, 8);
  }, [products, pick]);

  const add = (p: Product) => {
    setLines((prev) => prev.some((l) => l.productId === p.id) ? prev
      : [...prev, { productId: p.id, description: p.name, quantity: 1, unitCost: p.cost || 0 }]);
    setPick('');
  };
  const setLine = (i: number, patch: Partial<Line>) => setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const removeLine = (i: number) => setLines((prev) => prev.filter((_, idx) => idx !== i));
  const total = lines.reduce((s, l) => s + l.quantity * l.unitCost, 0);

  const submit = async () => {
    if (lines.length === 0) { toast.info('Agrega al menos un producto'); return; }
    setBusy(true);
    try {
      await api.post('/purchasing/returns', {
        purchaseId: purchaseId ? Number(purchaseId) : null,
        supplierId: supplierId || null, branchId, reason: reason || null,
        items: lines.map((l) => ({ productId: l.productId, description: l.description, quantity: l.quantity, unitCost: l.unitCost })),
      });
      toast.success('Devolución registrada', 'Se dio salida al inventario.');
      setLines([]); setReason(''); setPurchaseId('');
      loadHistory();
    } catch { toast.error('No se pudo registrar la devolución'); } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Devolución de compras</h1>
      <p className="page-sub">Devuelve mercancía al proveedor y ajusta el inventario</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="pur-head">
          <label className="field"><span>Proveedor</span>
            <select value={supplierId} onChange={(e) => setSupplierId(Number(e.target.value))}>
              <option value={0}>Sin proveedor</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select></label>
          <label className="field"><span>Almacén</span>
            <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select></label>
          <label className="field"><span>Compra origen (opcional)</span>
            <input value={purchaseId} onChange={(e) => setPurchaseId(e.target.value)} placeholder="Id de compra" /></label>
          <label className="field"><span>Motivo</span>
            <input value={reason} onChange={(e) => setReason(e.target.value)} /></label>
        </div>

        <div className="pur-add" style={{ marginTop: 'var(--space-4)' }}>
          <div className="cust-search" style={{ position: 'relative' }}>
            <Search size={16} />
            <input value={pick} onChange={(e) => setPick(e.target.value)} placeholder="Buscar producto a devolver…" />
            {filtered.length > 0 && (
              <div className="pur-suggest">
                {filtered.map((p) => <button key={p.id} onClick={() => add(p)}><span>{p.name}</span><span className="muted">{money(p.cost)}</span></button>)}
              </div>
            )}
          </div>
        </div>

        <table className="pur-table">
          <thead><tr><th>Artículo</th><th className="ta-right">Cant.</th><th className="ta-right">Costo</th><th className="ta-right">Importe</th><th></th></tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={l.productId}>
                <td>{l.description}</td>
                <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.001" value={l.quantity} onChange={(e) => setLine(i, { quantity: Number(e.target.value) || 0 })} /></td>
                <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.01" value={l.unitCost} onChange={(e) => setLine(i, { unitCost: Number(e.target.value) || 0 })} /></td>
                <td className="ta-right">{money(l.quantity * l.unitCost)}</td>
                <td className="ta-right"><button className="icon-btn icon-btn-danger" onClick={() => removeLine(i)} aria-label="Quitar"><Trash2 size={14} /></button></td>
              </tr>
            ))}
            {lines.length === 0 && <tr><td colSpan={5} className="ret-empty"><Plus size={18} /> Agrega productos a devolver.</td></tr>}
          </tbody>
        </table>

        <div className="pur-foot">
          <div />
          <div className="pur-totals">
            <div className="pur-total-row pur-total-grand"><span>Total devolución</span><strong>{money(total)}</strong></div>
            <div className="pur-actions">
              <button className="btn-accent" disabled={busy} onClick={submit}><Check size={16} /> Registrar devolución</button>
            </div>
          </div>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Undo2 size={18} /> Devoluciones recientes</h3>
        <table className="ret-table">
          <thead><tr><th>Folio</th><th>Proveedor</th><th>Motivo</th><th className="ta-right">Importe</th><th>Procesó</th></tr></thead>
          <tbody>
            {history.map((r) => (
              <tr key={r.id}>
                <td><strong>{r.folio}</strong></td>
                <td>{r.supplierName || '—'}</td>
                <td>{r.reason || <span className="muted">—</span>}</td>
                <td className="ta-right">{money(r.total)}</td>
                <td>{r.processedBy || '—'}</td>
              </tr>
            ))}
            {history.length === 0 && <tr><td colSpan={5} className="ret-empty">Sin devoluciones registradas.</td></tr>}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}
