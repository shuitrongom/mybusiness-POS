import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { FileCode, Upload, Check } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/customers.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Supplier { id: number; name: string; rfc: string | null; }
interface Branch { id: number; name: string; active: boolean; }
interface Concept { description: string; quantity: string; unitCost: string; satKey: string; }
interface Parsed {
  supplierRfc: string | null; supplierName: string | null; uuid: string | null;
  total: string | null; currency: string | null; concepts: Concept[];
}

/**
 * Compras XML: sube el CFDI (XML) del proveedor, lo previsualiza (emisor, folio fiscal y
 * conceptos) y lo registra como compra. Alimenta inventario y cuentas por pagar.
 */
export function PurchaseXmlPage() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [supplierId, setSupplierId] = useState<number>(0);
  const [branchId, setBranchId] = useState<number>(0);
  const [onCredit, setOnCredit] = useState(false);
  const [xml, setXml] = useState('');
  const [parsed, setParsed] = useState<Parsed | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    Promise.all([
      api.get<Supplier[]>('/purchasing/suppliers'),
      api.get<Branch[]>('/branches'),
    ]).then(([s, b]) => {
      setSuppliers(s.data);
      const active = b.data.filter((x) => x.active);
      setBranches(active); if (active[0]) setBranchId(active[0].id);
    }).catch(() => {});
  }, []);

  const onFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setXml(String(reader.result));
    reader.readAsText(file);
  };

  const parse = async () => {
    if (!xml.trim()) { toast.info('Sube o pega el XML del CFDI'); return; }
    try {
      const { data } = await api.post<Parsed>('/purchasing/cfdi/parse', { xml });
      setParsed(data);
      // Intenta emparejar el proveedor por RFC.
      if (data.supplierRfc) {
        const match = suppliers.find((s) => (s.rfc || '').toUpperCase() === data.supplierRfc!.toUpperCase());
        if (match) setSupplierId(match.id);
      }
      toast.success('CFDI leído', `${data.concepts.length} concepto(s) encontrados.`);
    } catch {
      toast.error('No se pudo leer el CFDI', 'Verifica que sea un XML de CFDI 4.0 válido.');
    }
  };

  const [matches, setMatches] = useState<Record<number, number>>({});
  const [products, setProducts] = useState<{ id: number; name: string }[]>([]);
  useEffect(() => {
    api.get<{ id: number; name: string }[]>('/catalog/products').then((r) => setProducts(r.data)).catch(() => {});
  }, []);

  const register = async () => {
    if (!parsed) return;
    if (!supplierId) { toast.info('Elige el proveedor'); return; }
    const unmatched = parsed.concepts.some((_, i) => !matches[i]);
    if (unmatched) { toast.info('Empareja cada concepto con un producto del catálogo'); return; }
    setBusy(true);
    try {
      await api.post('/purchasing/purchases/full', {
        supplierId, branchId, currency: parsed.currency || 'MXN', exchangeRate: 1,
        onCredit, cfdiUuid: parsed.uuid, invoiceRef: parsed.uuid,
        lines: parsed.concepts.map((c, i) => ({
          productId: matches[i], description: c.description,
          quantity: Number(c.quantity) || 1, unitCost: Number(c.unitCost) || 0, taxRate: 0.16,
        })),
      });
      toast.success('Compra registrada desde XML', 'Se afectó inventario y cuentas por pagar.');
      setParsed(null); setXml(''); setMatches({});
    } catch {
      toast.error('No se pudo registrar la compra', 'Revisa el emparejado de productos.');
    } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Compras XML</h1>
      <p className="page-sub">Importa el CFDI (XML) de tu proveedor y conviértelo en compra</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><FileCode size={18} /> Cargar CFDI</h3>
        <div className="pur-head" style={{ marginTop: 'var(--space-3)' }}>
          <label className="field"><span>Proveedor</span>
            <select value={supplierId} onChange={(e) => setSupplierId(Number(e.target.value))}>
              <option value={0}>Selecciona…</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select></label>
          <label className="field"><span>Almacén</span>
            <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select></label>
          <label className="switch-row" style={{ alignSelf: 'end' }}>
            <input type="checkbox" checked={onCredit} onChange={(e) => setOnCredit(e.target.checked)} />
            <span>A crédito</span>
          </label>
        </div>

        <div style={{ marginTop: 'var(--space-3)', display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
          <label className="btn-ghost" style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Upload size={16} /> Subir XML
            <input type="file" accept=".xml,text/xml" onChange={onFile} hidden />
          </label>
          <button className="btn-primary" onClick={parse}>Leer CFDI</button>
        </div>
        <textarea rows={4} value={xml} onChange={(e) => setXml(e.target.value)}
          placeholder="…o pega aquí el contenido del XML"
          style={{ width: '100%', marginTop: 'var(--space-3)', fontFamily: 'monospace', fontSize: 12 }} />
      </motion.div>

      {parsed && (
        <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Check size={18} /> Previsualización</h3>
          <div className="xml-meta">
            <div><span className="muted">Emisor</span><strong>{parsed.supplierName || '—'}</strong></div>
            <div><span className="muted">RFC</span><strong>{parsed.supplierRfc || '—'}</strong></div>
            <div><span className="muted">Folio fiscal</span><strong style={{ fontSize: 12 }}>{parsed.uuid || '—'}</strong></div>
            <div><span className="muted">Total CFDI</span><strong>{parsed.total ? money(Number(parsed.total)) : '—'}</strong></div>
          </div>
          <table className="pur-table" style={{ marginTop: 'var(--space-3)' }}>
            <thead><tr><th>Descripción</th><th>Producto del catálogo</th><th className="ta-right">Cantidad</th><th className="ta-right">Costo</th></tr></thead>
            <tbody>
              {parsed.concepts.map((c, i) => (
                <tr key={i}>
                  <td>{c.description}</td>
                  <td>
                    <select className="pur-in" style={{ width: 200 }} value={matches[i] ?? 0}
                      onChange={(e) => setMatches((m) => ({ ...m, [i]: Number(e.target.value) }))}>
                      <option value={0}>Elegir producto…</option>
                      {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                    </select>
                  </td>
                  <td className="ta-right">{c.quantity}</td><td className="ta-right">{money(Number(c.unitCost) || 0)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="drawer-note">Empareja cada concepto del CFDI con un producto de tu catálogo para afectar existencias y costo correctamente.</p>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)' }}>
            <button className="btn-ghost" onClick={() => setParsed(null)}>Descartar</button>
            <button className="btn-accent" disabled={busy} onClick={register}><Check size={16} /> Registrar compra</button>
          </div>
        </motion.div>
      )}
    </div>
  );
}
