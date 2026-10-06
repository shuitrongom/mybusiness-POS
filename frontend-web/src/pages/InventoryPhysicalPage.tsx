import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { ClipboardList, Play, Check, Search, FileText } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/products.css';

const num = (n: number) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 3 }).format(n || 0);

interface Branch { id: number; name: string; active: boolean; }
interface Category { id: number; name: string; }
interface CountRow { id: number; folio: string; branchName: string | null; status: string; createdAt: string; }
interface Line { productId: number; productName: string; sku: string | null; marbete: string | null; theoretical: number; counted: number | null; difference: number; }
interface Product { id: number; name: string; sku: string | null; }

type Tab = 'scope' | 'capture' | 'reports';

/**
 * Inventario físico (conteo): tres pasos como MyBusiness — 1) alcance (almacén y familia) e
 * iniciar; 2) captura (por producto, con existencia teórica vs real); 3) reportes (diferencias) y
 * aplicar. Al aplicar, ajusta las existencias a lo contado.
 */
export function InventoryPhysicalPage() {
  const [tab, setTab] = useState<Tab>('scope');
  const [branches, setBranches] = useState<Branch[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [branchId, setBranchId] = useState<number>(0);
  const [categoryId, setCategoryId] = useState<string>('');
  const [countId, setCountId] = useState<number | null>(null);
  const [counts, setCounts] = useState<CountRow[]>([]);
  const [lines, setLines] = useState<Line[]>([]);
  const [onlyDiff, setOnlyDiff] = useState(false);

  // Captura
  const [pick, setPick] = useState('');
  const [marbete, setMarbete] = useState('');
  const [counted, setCounted] = useState('');
  const [selected, setSelected] = useState<Product | null>(null);

  const loadCounts = () => api.get<CountRow[]>('/inventory/counts').then((r) => setCounts(r.data)).catch(() => setCounts([]));
  const loadLines = (id: number) => api.get<Line[]>(`/inventory/counts/${id}/lines`, { params: { onlyDifferences: onlyDiff } })
    .then((r) => setLines(r.data)).catch(() => setLines([]));

  useEffect(() => {
    Promise.all([
      api.get<Branch[]>('/branches'), api.get<Category[]>('/catalog/products/categories'),
      api.get<Product[]>('/catalog/products'),
    ]).then(([b, c, p]) => {
      const active = b.data.filter((x) => x.active); setBranches(active); if (active[0]) setBranchId(active[0].id);
      setCategories(c.data); setProducts(p.data);
    }).catch(() => {});
    loadCounts();
  }, []);

  useEffect(() => { if (countId) loadLines(countId); /* eslint-disable-next-line */ }, [onlyDiff]);

  const start = async () => {
    if (!branchId) { toast.info('Elige un almacén'); return; }
    try {
      const { data: id } = await api.post<number>('/inventory/counts/start', {
        branchId, categoryId: categoryId ? Number(categoryId) : null,
      });
      setCountId(id); setTab('capture'); loadLines(id); loadCounts();
      toast.success('Inventario físico iniciado', `Folio IF-${id}. Captura las existencias reales.`);
    } catch { toast.error('No se pudo iniciar el inventario físico'); }
  };

  const filtered = useMemo(() => {
    const q = pick.trim().toLowerCase();
    if (!q) return [];
    return products.filter((p) => p.name.toLowerCase().includes(q) || String(p.id) === q || (p.sku ?? '').toLowerCase().includes(q)).slice(0, 8);
  }, [products, pick]);

  const capture = async () => {
    if (!countId || !selected) { toast.info('Elige un producto'); return; }
    if (counted === '') { toast.info('Escribe la cantidad contada'); return; }
    try {
      await api.post(`/inventory/counts/${countId}/capture`, {
        productId: selected.id, marbete: marbete || null, counted: Number(counted),
      });
      toast.success('Captura aplicada', `${selected.name}: ${counted}.`);
      setSelected(null); setPick(''); setMarbete(''); setCounted('');
      loadLines(countId);
    } catch { toast.error('No se pudo capturar'); }
  };

  const apply = async () => {
    if (!countId) return;
    try {
      const { data } = await api.post<{ adjusted: number }>(`/inventory/counts/${countId}/apply`, {});
      toast.success('Inventario aplicado', `Se ajustaron ${data.adjusted} producto(s).`);
      setCountId(null); setLines([]); setTab('scope'); loadCounts();
    } catch { toast.error('No se pudo aplicar el inventario'); }
  };

  const diffs = lines.filter((l) => l.counted !== null && l.difference !== 0);

  return (
    <div>
      <h1 className="page-title">Inventario físico</h1>
      <p className="page-sub">Conteo de existencias con marbetes, diferencias y aplicación</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="drawer-tabs">
          <button className={`drawer-tab ${tab === 'scope' ? 'is-active' : ''}`} onClick={() => setTab('scope')}>1 · Almacén y familia</button>
          <button className={`drawer-tab ${tab === 'capture' ? 'is-active' : ''}`} onClick={() => setTab('capture')} disabled={!countId}>2 · Captura</button>
          <button className={`drawer-tab ${tab === 'reports' ? 'is-active' : ''}`} onClick={() => { setTab('reports'); if (countId) loadLines(countId); }} disabled={!countId}>3 · Reportes</button>
        </div>

        <div style={{ paddingTop: 'var(--space-4)' }}>
          {tab === 'scope' && (
            <div className="drawer-section">
              <div className="grid-2">
                <label className="field"><span>Almacén</span>
                  <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
                    {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                  </select></label>
                <label className="field"><span>Familia (opcional)</span>
                  <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                    <option value="">Todo el inventario</option>
                    {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select></label>
              </div>
              <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                <button className="btn-accent" onClick={start}><Play size={16} /> Iniciar inventario físico</button>
                {countId && <button className="btn-primary" onClick={() => setTab('capture')}>Continuar captura (IF-{countId})</button>}
              </div>

              <h3 className="sec-title" style={{ marginTop: 'var(--space-4)' }}><ClipboardList size={18} /> Conteos recientes</h3>
              <table className="ret-table">
                <thead><tr><th>Folio</th><th>Almacén</th><th>Estado</th><th className="ta-right">Acción</th></tr></thead>
                <tbody>
                  {counts.map((c) => (
                    <tr key={c.id}>
                      <td><strong>{c.folio}</strong></td>
                      <td>{c.branchName || '—'}</td>
                      <td><span className={`badge ${c.status === 'OPEN' ? 'badge-warning' : c.status === 'APPLIED' ? 'badge-success' : 'badge-muted'}`}>
                        {c.status === 'OPEN' ? 'Abierto' : c.status === 'APPLIED' ? 'Aplicado' : 'Cancelado'}</span></td>
                      <td className="ta-right">
                        {c.status === 'OPEN' && <button className="btn-primary btn-sm" onClick={() => { setCountId(c.id); setTab('capture'); loadLines(c.id); }}>Continuar</button>}
                      </td>
                    </tr>
                  ))}
                  {counts.length === 0 && <tr><td colSpan={4} className="ret-empty">Sin conteos.</td></tr>}
                </tbody>
              </table>
            </div>
          )}

          {tab === 'capture' && countId && (
            <div className="drawer-section">
              <div className="cust-search" style={{ position: 'relative', maxWidth: 480 }}>
                <Search size={16} />
                <input value={selected ? selected.name : pick} onChange={(e) => { setSelected(null); setPick(e.target.value); }}
                  placeholder="Buscar artículo por nombre, id o SKU…" />
                {filtered.length > 0 && !selected && (
                  <div className="pur-suggest">
                    {filtered.map((p) => <button key={p.id} onClick={() => { setSelected(p); setPick(''); }}><span>{p.name}</span><span className="muted">{p.sku ?? ''}</span></button>)}
                  </div>
                )}
              </div>
              <div className="grid-2">
                <label className="field"><span>Número de marbete</span>
                  <input value={marbete} onChange={(e) => setMarbete(e.target.value)} /></label>
                <label className="field"><span>Existencia real (conteo)</span>
                  <input type="number" step="0.001" value={counted} onChange={(e) => setCounted(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && capture()} /></label>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <button className="btn-accent" onClick={capture}><Check size={16} /> Aplicar captura</button>
              </div>

              <table className="ret-table" style={{ marginTop: 'var(--space-3)' }}>
                <thead><tr><th>Producto</th><th>Marbete</th><th className="ta-right">Teórica</th><th className="ta-right">Contada</th><th className="ta-right">Diferencia</th></tr></thead>
                <tbody>
                  {lines.filter((l) => l.counted !== null).map((l) => (
                    <tr key={l.productId}>
                      <td>{l.productName}</td>
                      <td>{l.marbete || '—'}</td>
                      <td className="ta-right">{num(l.theoretical)}</td>
                      <td className="ta-right">{l.counted !== null ? num(l.counted) : '—'}</td>
                      <td className="ta-right" style={{ color: l.difference < 0 ? 'var(--danger)' : l.difference > 0 ? 'var(--success)' : 'inherit' }}>
                        {l.difference > 0 ? '+' : ''}{num(l.difference)}
                      </td>
                    </tr>
                  ))}
                  {lines.filter((l) => l.counted !== null).length === 0 && <tr><td colSpan={5} className="ret-empty">Aún no capturas conteos.</td></tr>}
                </tbody>
              </table>
            </div>
          )}

          {tab === 'reports' && countId && (
            <div className="drawer-section">
              <div className="sec-title-row">
                <h3 className="sec-title"><FileText size={18} /> Diferencias del conteo</h3>
                <label className="switch-row" style={{ margin: 0 }}>
                  <input type="checkbox" checked={onlyDiff} onChange={(e) => setOnlyDiff(e.target.checked)} />
                  <span>Solo diferencias</span>
                </label>
              </div>
              <table className="ret-table">
                <thead><tr><th>Producto</th><th>SKU</th><th className="ta-right">Teórica</th><th className="ta-right">Contada</th><th className="ta-right">Diferencia</th></tr></thead>
                <tbody>
                  {lines.map((l) => (
                    <tr key={l.productId}>
                      <td>{l.productName}</td>
                      <td>{l.sku || '—'}</td>
                      <td className="ta-right">{num(l.theoretical)}</td>
                      <td className="ta-right">{l.counted !== null ? num(l.counted) : <span className="muted">sin contar</span>}</td>
                      <td className="ta-right" style={{ color: l.difference < 0 ? 'var(--danger)' : l.difference > 0 ? 'var(--success)' : 'inherit' }}>
                        {l.counted !== null ? `${l.difference > 0 ? '+' : ''}${num(l.difference)}` : '—'}
                      </td>
                    </tr>
                  ))}
                  {lines.length === 0 && <tr><td colSpan={5} className="ret-empty">Sin renglones.</td></tr>}
                </tbody>
              </table>
              <div className="pur-foot">
                <div />
                <div className="pur-totals">
                  <div className="pur-total-row"><span>Productos con diferencia</span><span>{diffs.length}</span></div>
                  <div className="pur-actions">
                    <button className="btn-accent" onClick={apply}><Check size={16} /> Aplicar inventario (ajustar existencias)</button>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
}
