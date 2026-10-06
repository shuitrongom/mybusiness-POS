import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Printer, Plus, Trash2, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { barcodeSvg } from '@/lib/barcode';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/inventory.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Product { id: number; name: string; sku: string | null; price: number; }
interface LabelFormat { id: number; name: string; widthMm: number; heightMm: number; columns: number; showPrice: boolean; showName: boolean; }
interface LabelData { productId: number; name: string; sku: string | null; price: number; barcode: string | null; copies: number; }

/**
 * Etiquetas de código de barras: arma una lista de productos con la cantidad de etiquetas por
 * cada uno, genera los códigos (Code128) y los imprime en la cuadrícula del formato elegido.
 */
export function InventoryLabelsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [formats, setFormats] = useState<LabelFormat[]>([]);
  const [formatId, setFormatId] = useState<number>(0);
  const [items, setItems] = useState<{ product: Product; copies: number }[]>([]);
  const [pick, setPick] = useState('');
  const [labels, setLabels] = useState<LabelData[]>([]);

  useEffect(() => {
    Promise.all([api.get<Product[]>('/catalog/products'), api.get<LabelFormat[]>('/inventory/label-formats')])
      .then(([p, f]) => { setProducts(p.data); setFormats(f.data); if (f.data[0]) setFormatId(f.data[0].id); })
      .catch(() => {});
  }, []);

  const filtered = useMemo(() => {
    const q = pick.trim().toLowerCase();
    if (!q) return [];
    return products.filter((p) => p.name.toLowerCase().includes(q) || (p.sku ?? '').toLowerCase().includes(q)).slice(0, 8);
  }, [products, pick]);

  const format = formats.find((f) => f.id === formatId);

  const add = (p: Product) => {
    setItems((prev) => prev.some((it) => it.product.id === p.id) ? prev : [...prev, { product: p, copies: 1 }]);
    setPick('');
  };
  const setCopies = (id: number, copies: number) => setItems((prev) => prev.map((it) => it.product.id === id ? { ...it, copies: Math.max(1, copies) } : it));
  const removeItem = (id: number) => setItems((prev) => prev.filter((it) => it.product.id !== id));

  const generate = async () => {
    if (items.length === 0) { toast.info('Agrega productos'); return; }
    try {
      const { data } = await api.post<LabelData[]>('/inventory/labels', {
        items: items.map((it) => ({ productId: it.product.id, copies: it.copies })),
      });
      setLabels(data);
      toast.success('Etiquetas generadas', 'Listas para imprimir.');
    } catch { toast.error('No se pudieron generar las etiquetas'); }
  };

  // Expande cada producto por su número de copias.
  const printable = useMemo(() => {
    const out: LabelData[] = [];
    for (const l of labels) {
      for (let i = 0; i < (l.copies || 1); i++) out.push(l);
    }
    return out;
  }, [labels]);

  return (
    <div>
      <div className="page-head-row">
        <div>
          <h1 className="page-title">Etiquetas de código de barras</h1>
          <p className="page-sub">Genera e imprime etiquetas con código de barras</p>
        </div>
        {printable.length > 0 && (
          <button className="btn-accent" onClick={() => window.print()}><Printer size={16} /> Imprimir</button>
        )}
      </div>

      <motion.div className="card no-print" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="lbl-toolbar">
          <div className="cust-search" style={{ position: 'relative', flex: 1 }}>
            <Search size={16} />
            <input value={pick} onChange={(e) => setPick(e.target.value)} placeholder="Buscar producto por nombre o SKU…" />
            {filtered.length > 0 && (
              <div className="pur-suggest">
                {filtered.map((p) => <button key={p.id} onClick={() => add(p)}><span>{p.name}</span><span className="muted">{money(p.price)}</span></button>)}
              </div>
            )}
          </div>
          <select className="pur-in" style={{ width: 220 }} value={formatId} onChange={(e) => setFormatId(Number(e.target.value))}>
            {formats.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </select>
        </div>

        <table className="cust-table" style={{ marginTop: 'var(--space-3)' }}>
          <thead><tr><th>Producto</th><th>SKU</th><th className="ta-right">Precio</th><th className="ta-right">Etiquetas</th><th></th></tr></thead>
          <tbody>
            {items.map((it) => (
              <tr key={it.product.id}>
                <td><strong>{it.product.name}</strong></td>
                <td>{it.product.sku || '—'}</td>
                <td className="ta-right">{money(it.product.price)}</td>
                <td className="ta-right"><input className="pur-in" style={{ width: 70 }} type="number" min={1} value={it.copies} onChange={(e) => setCopies(it.product.id, Number(e.target.value) || 1)} /></td>
                <td className="ta-right"><button className="icon-btn icon-btn-danger" onClick={() => removeItem(it.product.id)} aria-label="Quitar"><Trash2 size={14} /></button></td>
              </tr>
            ))}
            {items.length === 0 && <tr><td colSpan={5} className="ret-empty"><Plus size={18} /> Agrega productos para etiquetar.</td></tr>}
          </tbody>
        </table>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 'var(--space-3)' }}>
          <button className="btn-primary" onClick={generate} disabled={items.length === 0}>Generar etiquetas</button>
        </div>
      </motion.div>

      {printable.length > 0 && (
        <div className="lbl-sheet" style={{ gridTemplateColumns: `repeat(${format?.columns ?? 3}, 1fr)` }}>
          {printable.map((l, i) => (
            <div className="lbl" key={i} style={{ width: `${format?.widthMm ?? 50}mm`, minHeight: `${format?.heightMm ?? 30}mm` }}>
              {format?.showName !== false && <div className="lbl-name">{l.name}</div>}
              <div className="lbl-barcode" dangerouslySetInnerHTML={{ __html: barcodeSvg(l.barcode || l.sku || String(l.productId), { height: 40, moduleWidth: 1.3 }) }} />
              {format?.showPrice !== false && <div className="lbl-price">{money(l.price)}</div>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
