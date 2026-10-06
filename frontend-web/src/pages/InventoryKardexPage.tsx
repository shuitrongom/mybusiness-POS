import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { History, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/returns.css';

const num = (n: number) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 3 }).format(n || 0);

interface Product { id: number; name: string; sku: string | null; }
interface Move {
  id: number; type: string; quantity: number; balanceAfter: number;
  reason: string | null; reference: string | null; actor: string | null;
  branchName: string | null; createdAt: string;
}

const TYPE_LABEL: Record<string, string> = {
  PURCHASE: 'Compra', SALE: 'Salida/Venta', ADJUSTMENT: 'Ajuste',
  TRANSFER_IN: 'Traspaso entra', TRANSFER_OUT: 'Traspaso sale', RETURN: 'Entrada/Devolución',
};

/**
 * Kardex: historial de movimientos de inventario de un producto (entradas, salidas, ajustes,
 * traspasos) con el saldo resultante tras cada movimiento.
 */
export function InventoryKardexPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [product, setProduct] = useState<Product | null>(null);
  const [pick, setPick] = useState('');
  const [moves, setMoves] = useState<Move[]>([]);

  useEffect(() => { api.get<Product[]>('/catalog/products').then((r) => setProducts(r.data)).catch(() => {}); }, []);

  const filtered = useMemo(() => {
    const q = pick.trim().toLowerCase();
    if (!q) return [];
    return products.filter((p) => p.name.toLowerCase().includes(q) || String(p.id) === q || (p.sku ?? '').toLowerCase().includes(q)).slice(0, 8);
  }, [products, pick]);

  const choose = (p: Product) => {
    setProduct(p); setPick('');
    api.get<Move[]>(`/inventory/kardex-detail/${p.id}`).then((r) => setMoves(r.data)).catch(() => setMoves([]));
  };

  return (
    <div>
      <h1 className="page-title">Kardex de movimientos</h1>
      <p className="page-sub">Historial de entradas, salidas, ajustes y traspasos por producto</p>

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
      </motion.div>

      {product && (
        <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><History size={18} /> {product.name}</h3>
          <table className="ret-table">
            <thead><tr><th>Fecha</th><th>Movimiento</th><th>Almacén</th><th className="ta-right">Cantidad</th><th className="ta-right">Saldo</th><th>Referencia</th><th>Usuario</th></tr></thead>
            <tbody>
              {moves.map((m) => (
                <tr key={m.id}>
                  <td>{new Date(m.createdAt).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' })}</td>
                  <td>{TYPE_LABEL[m.type] || m.type}</td>
                  <td>{m.branchName || '—'}</td>
                  <td className="ta-right" style={{ color: m.quantity < 0 ? 'var(--danger)' : 'var(--success)' }}>
                    {m.quantity > 0 ? '+' : ''}{num(m.quantity)}
                  </td>
                  <td className="ta-right"><strong>{num(m.balanceAfter)}</strong></td>
                  <td>{m.reference || m.reason || <span className="muted">—</span>}</td>
                  <td>{m.actor || '—'}</td>
                </tr>
              ))}
              {moves.length === 0 && <tr><td colSpan={7} className="ret-empty">Sin movimientos.</td></tr>}
            </tbody>
          </table>
        </motion.div>
      )}
    </div>
  );
}
