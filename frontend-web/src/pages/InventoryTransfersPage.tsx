import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { ArrowLeftRight, Search, Check } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { PrintableVoucher } from '@/components/PrintableVoucher';
import { type VoucherData } from '@/components/Voucher';
import '@/pages/dashboard.css';
import '@/pages/customers.css';

interface Product { id: number; name: string; sku: string | null; }
interface Branch { id: number; name: string; active: boolean; }

/**
 * Traspasos entre sucursales: mueve un producto de un almacén a otro (salida en origen, entrada
 * en destino, atómico). Reutiliza el endpoint de traspaso del inventario.
 */
export function InventoryTransfersPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [product, setProduct] = useState<Product | null>(null);
  const [pick, setPick] = useState('');
  const [fromBranch, setFromBranch] = useState<number>(0);
  const [toBranch, setToBranch] = useState<number>(0);
  const [quantity, setQuantity] = useState('');
  const [busy, setBusy] = useState(false);
  const [voucher, setVoucher] = useState<VoucherData | null>(null);

  useEffect(() => {
    Promise.all([api.get<Product[]>('/catalog/products'), api.get<Branch[]>('/branches')])
      .then(([p, b]) => {
        setProducts(p.data);
        const active = b.data.filter((x) => x.active);
        setBranches(active);
        if (active[0]) setFromBranch(active[0].id);
        if (active[1]) setToBranch(active[1].id);
      }).catch(() => {});
  }, []);

  const filtered = useMemo(() => {
    const q = pick.trim().toLowerCase();
    if (!q) return [];
    return products.filter((p) => p.name.toLowerCase().includes(q) || String(p.id) === q).slice(0, 8);
  }, [products, pick]);

  const submit = async () => {
    if (!product) { toast.info('Elige un producto'); return; }
    if (fromBranch === toBranch) { toast.info('Las sucursales deben ser distintas'); return; }
    if (!quantity || Number(quantity) <= 0) { toast.info('Escribe la cantidad'); return; }
    setBusy(true);
    try {
      await api.post('/inventory/transfer', {
        productId: product.id, fromBranchId: fromBranch, toBranchId: toBranch, quantity: Number(quantity),
      });
      const fromName = branches.find((b) => b.id === fromBranch)?.name ?? '—';
      const toName = branches.find((b) => b.id === toBranch)?.name ?? '—';
      // Comprobante del traspaso para respaldo del movimiento de mercancía.
      setVoucher({
        title: 'TRASPASO DE MERCANCÍA',
        storeName: 'Comprobante de traspaso',
        folio: `TRA-${Date.now().toString().slice(-6)}`,
        dateTime: new Date(),
        items: [{ description: product.name, quantity: Number(quantity) }],
        rows: [
          { label: 'Origen', value: fromName },
          { label: 'Destino', value: toName, strong: true },
        ],
        signature: true,
        footer: 'Comprobante de traspaso entre sucursales · respaldo.',
      });
      toast.success('Traspaso realizado', `${product.name}: ${quantity} unidad(es) movidas.`);
      setQuantity('');
    } catch { toast.error('No se pudo realizar el traspaso', 'Verifica que haya existencia en el origen.'); } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Traspasos entre sucursales</h1>
      <p className="page-sub">Mueve mercancía de un almacén a otro</p>

      <motion.div className="card" style={{ maxWidth: 640 }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><ArrowLeftRight size={18} /> Nuevo traspaso</h3>
        <div className="drawer-section" style={{ marginTop: 'var(--space-3)' }}>
          <div className="cust-search" style={{ position: 'relative' }}>
            <Search size={16} />
            <input value={product ? product.name : pick} onChange={(e) => { setProduct(null); setPick(e.target.value); }}
              placeholder="Buscar producto…" />
            {filtered.length > 0 && !product && (
              <div className="pur-suggest">
                {filtered.map((p) => <button key={p.id} onClick={() => { setProduct(p); setPick(''); }}><span>{p.name}</span><span className="muted">{p.sku ?? ''}</span></button>)}
              </div>
            )}
          </div>
          <div className="grid-2">
            <label className="field"><span>Almacén origen</span>
              <select value={fromBranch} onChange={(e) => setFromBranch(Number(e.target.value))}>
                {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select></label>
            <label className="field"><span>Almacén destino</span>
              <select value={toBranch} onChange={(e) => setToBranch(Number(e.target.value))}>
                {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select></label>
          </div>
          <label className="field"><span>Cantidad</span>
            <input type="number" step="0.001" value={quantity} onChange={(e) => setQuantity(e.target.value)} /></label>
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button className="btn-accent" disabled={busy} onClick={submit}><Check size={16} /> Realizar traspaso</button>
          </div>
        </div>
      </motion.div>

      {voucher && <PrintableVoucher data={voucher} onDone={() => setVoucher(null)} />}
    </div>
  );
}
