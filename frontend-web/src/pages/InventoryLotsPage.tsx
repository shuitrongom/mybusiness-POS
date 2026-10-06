import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { PackageSearch, Plus, Search, TriangleAlert } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/collections.css';

const num = (n: number) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 3 }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);

interface Product { id: number; name: string; sku: string | null; }
interface Branch { id: number; name: string; active: boolean; }
interface Lot {
  id: number; productName: string; lotCode: string | null; expirationDate: string | null;
  quantity: number; entryPort: string | null; pedimento: string | null;
}

function daysTo(dateStr: string | null): number | null {
  if (!dateStr) return null;
  return Math.ceil((new Date(dateStr).getTime() - Date.now()) / 86400000);
}

/**
 * Números de lote: captura lotes con caducidad (entra al inventario) y lista los lotes con su
 * existencia y días para caducar, resaltando los próximos a vencer.
 */
export function InventoryLotsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [product, setProduct] = useState<Product | null>(null);
  const [pick, setPick] = useState('');
  const [branchId, setBranchId] = useState<number>(0);
  const [form, setForm] = useState({ lotCode: '', expiration: today(), quantity: '', entryPort: '', pedimento: '' });
  const [lots, setLots] = useState<Lot[]>([]);
  const [busy, setBusy] = useState(false);

  const loadLots = () => api.get<Lot[]>('/inventory/lots', { params: { onlyWithStock: false } })
    .then((r) => setLots(r.data)).catch(() => setLots([]));

  useEffect(() => {
    Promise.all([api.get<Product[]>('/catalog/products'), api.get<Branch[]>('/branches')])
      .then(([p, b]) => { setProducts(p.data); const active = b.data.filter((x) => x.active); setBranches(active); if (active[0]) setBranchId(active[0].id); })
      .catch(() => {});
    loadLots();
  }, []);

  const filtered = useMemo(() => {
    const q = pick.trim().toLowerCase();
    if (!q) return [];
    return products.filter((p) => p.name.toLowerCase().includes(q) || String(p.id) === q).slice(0, 8);
  }, [products, pick]);

  const save = async () => {
    if (!product) { toast.info('Elige un producto'); return; }
    if (!form.quantity || Number(form.quantity) <= 0) { toast.info('Escribe la cantidad'); return; }
    setBusy(true);
    try {
      await api.post('/inventory/lots', {
        productId: product.id, branchId, lotCode: form.lotCode || null,
        expiration: form.expiration || null, quantity: Number(form.quantity),
        entryPort: form.entryPort || null, pedimento: form.pedimento || null,
      });
      toast.success('Lote registrado', 'Entró al inventario.');
      setForm({ lotCode: '', expiration: today(), quantity: '', entryPort: '', pedimento: '' });
      loadLots();
    } catch { toast.error('No se pudo registrar el lote'); } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Números de lote</h1>
      <p className="page-sub">Captura lotes con caducidad y controla lo próximo a vencer</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Plus size={18} /> Capturar lote</h3>
        <div className="cust-search" style={{ position: 'relative', maxWidth: 480, marginTop: 'var(--space-3)' }}>
          <Search size={16} />
          <input value={product ? product.name : pick} onChange={(e) => { setProduct(null); setPick(e.target.value); }}
            placeholder="Buscar producto…" />
          {filtered.length > 0 && !product && (
            <div className="pur-suggest">
              {filtered.map((p) => <button key={p.id} onClick={() => { setProduct(p); setPick(''); }}><span>{p.name}</span><span className="muted">{p.sku ?? ''}</span></button>)}
            </div>
          )}
        </div>
        {product && (
          <div className="drawer-section" style={{ marginTop: 'var(--space-4)' }}>
            <div className="grid-2">
              <label className="field"><span>Almacén</span>
                <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
                  {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                </select></label>
              <label className="field"><span>Código de lote</span>
                <input value={form.lotCode} onChange={(e) => setForm({ ...form, lotCode: e.target.value })} /></label>
            </div>
            <div className="grid-2">
              <label className="field"><span>Cantidad</span>
                <input type="number" step="0.001" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} /></label>
              <label className="field"><span>Caducidad</span>
                <input type="date" value={form.expiration} onChange={(e) => setForm({ ...form, expiration: e.target.value })} /></label>
            </div>
            <div className="grid-2">
              <label className="field"><span>Puerto de entrada</span>
                <input value={form.entryPort} onChange={(e) => setForm({ ...form, entryPort: e.target.value })} /></label>
              <label className="field"><span>Pedimento</span>
                <input value={form.pedimento} onChange={(e) => setForm({ ...form, pedimento: e.target.value })} /></label>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button className="btn-accent" disabled={busy} onClick={save}><Plus size={16} /> Salvar lote</button>
            </div>
          </div>
        )}
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><PackageSearch size={18} /> Lotes registrados</h3>
        <table className="ret-table">
          <thead><tr><th>Producto</th><th>Lote</th><th>Caducidad</th><th className="ta-right">Existencia</th><th>Pedimento</th></tr></thead>
          <tbody>
            {lots.map((l) => {
              const d = daysTo(l.expirationDate);
              const soon = d !== null && d <= 30;
              return (
                <tr key={l.id}>
                  <td><strong>{l.productName}</strong></td>
                  <td>{l.lotCode || <span className="muted">—</span>}</td>
                  <td>
                    {l.expirationDate ? (
                      <span className={soon ? 'coll-due-over' : ''}>
                        {soon && <TriangleAlert size={12} />} {l.expirationDate}
                        {d !== null && <span className="muted"> ({d}d)</span>}
                      </span>
                    ) : <span className="muted">—</span>}
                  </td>
                  <td className="ta-right">{num(l.quantity)}</td>
                  <td>{l.pedimento || <span className="muted">—</span>}</td>
                </tr>
              );
            })}
            {lots.length === 0 && <tr><td colSpan={5} className="ret-empty">Sin lotes registrados.</td></tr>}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}
