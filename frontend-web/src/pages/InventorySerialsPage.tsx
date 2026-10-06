import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Barcode, Plus, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/returns.css';

interface Product { id: number; name: string; sku: string | null; }
interface Serial { id: number; serial: string; status: string; entryPort: string | null; pedimento: string | null; }

const STATUS_LABEL: Record<string, string> = { IN_STOCK: 'En stock', SOLD: 'Vendida', RETURNED: 'Devuelta' };

/**
 * Números de serie: asocia series a un producto (lista manual o por rango prefijo+desde/hasta),
 * con puerto de entrada y pedimento aduanal para importaciones. Muestra las series del producto.
 */
export function InventorySerialsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [product, setProduct] = useState<Product | null>(null);
  const [pick, setPick] = useState('');
  const [mode, setMode] = useState<'list' | 'range'>('list');
  const [list, setList] = useState('');
  const [range, setRange] = useState({ prefix: '', from: '', to: '' });
  const [entryPort, setEntryPort] = useState('');
  const [pedimento, setPedimento] = useState('');
  const [serials, setSerials] = useState<Serial[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => { api.get<Product[]>('/catalog/products').then((r) => setProducts(r.data)).catch(() => {}); }, []);

  const filtered = useMemo(() => {
    const q = pick.trim().toLowerCase();
    if (!q) return [];
    return products.filter((p) => p.name.toLowerCase().includes(q) || String(p.id) === q).slice(0, 8);
  }, [products, pick]);

  const loadSerials = (id: number) => {
    api.get<Serial[]>(`/inventory/serials/${id}`).then((r) => setSerials(r.data)).catch(() => setSerials([]));
  };
  const choose = (p: Product) => { setProduct(p); setPick(''); loadSerials(p.id); };

  const save = async () => {
    if (!product) { toast.info('Elige un producto'); return; }
    setBusy(true);
    try {
      const body: Record<string, unknown> = { productId: product.id, entryPort: entryPort || null, pedimento: pedimento || null };
      if (mode === 'range') {
        body.prefix = range.prefix || null; body.rangeFrom = Number(range.from); body.rangeTo = Number(range.to);
      } else {
        body.serials = list.split(/\r?\n|,/).map((s) => s.trim()).filter(Boolean);
      }
      const { data } = await api.post<{ added: number }>('/inventory/serials', body);
      toast.success('Series agregadas', `Se registraron ${data.added} serie(s).`);
      setList(''); setRange({ prefix: '', from: '', to: '' });
      loadSerials(product.id);
    } catch { toast.error('No se pudieron guardar las series'); } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Números de serie</h1>
      <p className="page-sub">Captura series por producto, con puerto de entrada y pedimento</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="cust-search" style={{ position: 'relative', maxWidth: 480 }}>
          <Search size={16} />
          <input value={product ? product.name : pick} onChange={(e) => { setProduct(null); setPick(e.target.value); }}
            placeholder="Buscar producto…" />
          {filtered.length > 0 && !product && (
            <div className="pur-suggest">
              {filtered.map((p) => <button key={p.id} onClick={() => choose(p)}><span>{p.name}</span><span className="muted">{p.sku ?? ''}</span></button>)}
            </div>
          )}
        </div>

        {product && (
          <div className="drawer-section" style={{ marginTop: 'var(--space-4)' }}>
            <div className="pos-methods" style={{ padding: 0, gridTemplateColumns: '1fr 1fr', maxWidth: 320 }}>
              <button className={`pos-method ${mode === 'list' ? 'is-active' : ''}`} onClick={() => setMode('list')}>Lista</button>
              <button className={`pos-method ${mode === 'range' ? 'is-active' : ''}`} onClick={() => setMode('range')}>Rango</button>
            </div>
            {mode === 'list' ? (
              <label className="field"><span>Series (una por línea o separadas por coma)</span>
                <textarea rows={5} value={list} onChange={(e) => setList(e.target.value)} placeholder={'SN0001\nSN0002\nSN0003'} /></label>
            ) : (
              <div className="grid-2">
                <label className="field"><span>Prefijo</span>
                  <input value={range.prefix} onChange={(e) => setRange({ ...range, prefix: e.target.value })} placeholder="SN" /></label>
                <div className="grid-2">
                  <label className="field"><span>Desde</span>
                    <input type="number" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} /></label>
                  <label className="field"><span>Hasta</span>
                    <input type="number" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} /></label>
                </div>
              </div>
            )}
            <div className="grid-2">
              <label className="field"><span>Puerto de entrada</span>
                <input value={entryPort} onChange={(e) => setEntryPort(e.target.value)} /></label>
              <label className="field"><span>Número de pedimento</span>
                <input value={pedimento} onChange={(e) => setPedimento(e.target.value)} /></label>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button className="btn-accent" disabled={busy} onClick={save}><Plus size={16} /> Salvar series</button>
            </div>
          </div>
        )}
      </motion.div>

      {product && (
        <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Barcode size={18} /> Series de {product.name} ({serials.length})</h3>
          <table className="ret-table">
            <thead><tr><th>Serie</th><th>Estado</th><th>Puerto</th><th>Pedimento</th></tr></thead>
            <tbody>
              {serials.map((s) => (
                <tr key={s.id}>
                  <td><strong>{s.serial}</strong></td>
                  <td><span className={`badge ${s.status === 'IN_STOCK' ? 'badge-success' : 'badge-muted'}`}>{STATUS_LABEL[s.status] || s.status}</span></td>
                  <td>{s.entryPort || <span className="muted">—</span>}</td>
                  <td>{s.pedimento || <span className="muted">—</span>}</td>
                </tr>
              ))}
              {serials.length === 0 && <tr><td colSpan={4} className="ret-empty">Sin series registradas.</td></tr>}
            </tbody>
          </table>
        </motion.div>
      )}
    </div>
  );
}
