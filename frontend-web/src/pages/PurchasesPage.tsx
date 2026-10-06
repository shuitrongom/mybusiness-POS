import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { ShoppingCart, Plus, Trash2, Check, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Supplier { id: number; name: string; rfc: string | null; }
interface Product { id: number; name: string; cost: number; extras?: { taxRate: number }; }
interface Branch { id: number; name: string; active: boolean; }

interface Line {
  productId: number; description: string; quantity: number; unitCost: number;
  discount: number; discountExtra: number; taxRate: number;
}

const VAT_OPTIONS = [
  { value: 0.16, label: 'IVA 16%' },
  { value: 0.08, label: 'IVA 8%' },
  { value: 0, label: 'IVA 0% / Exento' },
];

/**
 * Compras — recepción de mercancía enterprise: proveedor, almacén, moneda y crédito; grilla de
 * renglones con costo, descuento, descuento adicional e impuesto (IVA MX); totales en vivo con
 * IVA, retención y donativo. Al recibir afecta inventario, costo y cuentas por pagar.
 */
export function PurchasesPage() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);

  const [supplierId, setSupplierId] = useState<number>(0);
  const [branchId, setBranchId] = useState<number>(0);
  const [invoiceRef, setInvoiceRef] = useState('');
  const [currency, setCurrency] = useState('MXN');
  const [onCredit, setOnCredit] = useState(false);
  const [retention, setRetention] = useState('');
  const [donation, setDonation] = useState('');
  const [globalDiscount, setGlobalDiscount] = useState('');
  const [lines, setLines] = useState<Line[]>([]);

  const [productPick, setProductPick] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const [sup, prod, br] = await Promise.all([
          api.get<Supplier[]>('/purchasing/suppliers'),
          api.get<Product[]>('/catalog/products'),
          api.get<Branch[]>('/branches'),
        ]);
        setSuppliers(sup.data);
        setProducts(prod.data);
        const active = br.data.filter((b) => b.active);
        setBranches(active);
        if (active[0]) setBranchId(active[0].id);
      } catch { toast.error('No se pudo cargar el catálogo'); }
    })();
  }, []);

  const filteredProducts = useMemo(() => {
    const q = productPick.trim().toLowerCase();
    if (!q) return [];
    return products.filter((p) => p.name.toLowerCase().includes(q) || String(p.id) === q).slice(0, 8);
  }, [products, productPick]);

  const addProduct = (p: Product) => {
    setLines((prev) => {
      const existing = prev.find((l) => l.productId === p.id);
      if (existing) return prev.map((l) => (l.productId === p.id ? { ...l, quantity: l.quantity + 1 } : l));
      return [...prev, {
        productId: p.id, description: p.name, quantity: 1, unitCost: p.cost || 0,
        discount: 0, discountExtra: 0, taxRate: p.extras?.taxRate ?? 0.16,
      }];
    });
    setProductPick('');
  };
  const setLine = (i: number, patch: Partial<Line>) =>
    setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const removeLine = (i: number) => setLines((prev) => prev.filter((_, idx) => idx !== i));

  // Totales
  const totals = useMemo(() => {
    let subtotal = 0; let tax = 0;
    for (const l of lines) {
      const base = Math.max(l.quantity * l.unitCost - l.discount - l.discountExtra, 0);
      subtotal += base;
      tax += base * l.taxRate;
    }
    const gd = Number(globalDiscount) || 0;
    subtotal = Math.max(subtotal - gd, 0);
    const ret = Number(retention) || 0;
    const don = Number(donation) || 0;
    const total = subtotal + tax + don - ret;
    return { subtotal, tax, ret, don, total };
  }, [lines, globalDiscount, retention, donation]);

  const submit = async (asOrder: boolean) => {
    if (!supplierId) { toast.info('Elige un proveedor'); return; }
    if (lines.length === 0) { toast.info('Agrega al menos un producto'); return; }
    setBusy(true);
    const payload = {
      supplierId, branchId, invoiceRef: invoiceRef || null, currency, exchangeRate: 1,
      onCredit, discount: Number(globalDiscount) || 0, donation: Number(donation) || 0,
      retention: Number(retention) || 0, notes: null, expectedDate: null,
      lines: lines.map((l) => ({
        productId: l.productId, description: l.description, quantity: l.quantity,
        unitCost: l.unitCost, discount: l.discount, discountExtra: l.discountExtra, taxRate: l.taxRate,
      })),
    };
    try {
      const url = asOrder ? '/purchasing/orders' : '/purchasing/purchases/full';
      await api.post(url, payload);
      toast.success(asOrder ? 'Orden de compra creada' : 'Compra recibida',
        asOrder ? 'La orden quedó pendiente de recibir.' : 'Se actualizó inventario, costo y cuentas por pagar.');
      setLines([]); setInvoiceRef(''); setRetention(''); setDonation(''); setGlobalDiscount(''); setOnCredit(false);
    } catch {
      toast.error('No se pudo guardar la compra', 'Revisa los datos.');
    } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Compras</h1>
      <p className="page-sub">Recibe mercancía con impuestos, retención y donativo</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="pur-head">
          <label className="field"><span>Proveedor *</span>
            <select value={supplierId} onChange={(e) => setSupplierId(Number(e.target.value))}>
              <option value={0}>Selecciona…</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select></label>
          <label className="field"><span>Almacén / sucursal</span>
            <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select></label>
          <label className="field"><span>Factura / remisión</span>
            <input value={invoiceRef} onChange={(e) => setInvoiceRef(e.target.value)} /></label>
          <label className="field"><span>Moneda</span>
            <select value={currency} onChange={(e) => setCurrency(e.target.value)}>
              <option value="MXN">MXN</option><option value="USD">USD</option>
            </select></label>
          <label className="switch-row" style={{ alignSelf: 'end' }}>
            <input type="checkbox" checked={onCredit} onChange={(e) => setOnCredit(e.target.checked)} />
            <span>A crédito</span>
          </label>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><ShoppingCart size={18} /> Renglones</h3>
        <div className="pur-add">
          <div className="cust-search" style={{ position: 'relative' }}>
            <Search size={16} />
            <input value={productPick} onChange={(e) => setProductPick(e.target.value)}
              placeholder="Buscar producto por nombre o id para agregar…" />
            {filteredProducts.length > 0 && (
              <div className="pur-suggest">
                {filteredProducts.map((p) => (
                  <button key={p.id} onClick={() => addProduct(p)}>
                    <span>{p.name}</span><span className="muted">{money(p.cost)}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="cust-table-wrap">
          <table className="pur-table">
            <thead>
              <tr>
                <th>Artículo</th><th className="ta-right">Cant.</th><th className="ta-right">Costo</th>
                <th className="ta-right">Desc.</th><th className="ta-right">Desc. adic.</th>
                <th>Impuesto</th><th className="ta-right">Importe</th><th></th>
              </tr>
            </thead>
            <tbody>
              {lines.map((l, i) => {
                const base = Math.max(l.quantity * l.unitCost - l.discount - l.discountExtra, 0);
                return (
                  <tr key={l.productId}>
                    <td>{l.description}</td>
                    <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.001" value={l.quantity} onChange={(e) => setLine(i, { quantity: Number(e.target.value) || 0 })} /></td>
                    <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.01" value={l.unitCost} onChange={(e) => setLine(i, { unitCost: Number(e.target.value) || 0 })} /></td>
                    <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.01" value={l.discount} onChange={(e) => setLine(i, { discount: Number(e.target.value) || 0 })} /></td>
                    <td className="ta-right"><input className="pur-in" type="number" min={0} step="0.01" value={l.discountExtra} onChange={(e) => setLine(i, { discountExtra: Number(e.target.value) || 0 })} /></td>
                    <td>
                      <select className="pur-in" value={l.taxRate} onChange={(e) => setLine(i, { taxRate: Number(e.target.value) })}>
                        {VAT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                      </select>
                    </td>
                    <td className="ta-right">{money(base)}</td>
                    <td className="ta-right"><button className="icon-btn icon-btn-danger" onClick={() => removeLine(i)} aria-label="Quitar"><Trash2 size={14} /></button></td>
                  </tr>
                );
              })}
              {lines.length === 0 && (
                <tr><td colSpan={8} className="ret-empty"><Plus size={18} /> Busca y agrega productos arriba.</td></tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="pur-foot">
          <div className="pur-foot-fields">
            <label className="field"><span>Descuento global</span>
              <input type="number" min={0} step="0.01" value={globalDiscount} onChange={(e) => setGlobalDiscount(e.target.value)} /></label>
            <label className="field"><span>Retención (IVA/ISR)</span>
              <input type="number" min={0} step="0.01" value={retention} onChange={(e) => setRetention(e.target.value)} /></label>
            <label className="field"><span>Donativo</span>
              <input type="number" min={0} step="0.01" value={donation} onChange={(e) => setDonation(e.target.value)} /></label>
          </div>
          <div className="pur-totals">
            <div className="pur-total-row"><span>Subtotal</span><span>{money(totals.subtotal)}</span></div>
            <div className="pur-total-row"><span>Impuesto (IVA)</span><span>{money(totals.tax)}</span></div>
            {totals.ret > 0 && <div className="pur-total-row"><span>Retención</span><span>- {money(totals.ret)}</span></div>}
            {totals.don > 0 && <div className="pur-total-row"><span>Donativo</span><span>{money(totals.don)}</span></div>}
            <div className="pur-total-row pur-total-grand"><span>Total</span><strong>{money(totals.total)}</strong></div>
            <div className="pur-actions">
              <button className="btn-ghost" disabled={busy} onClick={() => submit(true)}>Guardar como orden</button>
              <button className="btn-accent" disabled={busy} onClick={() => submit(false)}>
                <Check size={16} /> Recibir compra
              </button>
            </div>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
