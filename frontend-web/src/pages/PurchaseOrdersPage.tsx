import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { FileText, Check, PackageCheck } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Order {
  id: number; folio: string; supplierName: string; invoiceRef: string | null;
  total: number; status: string; createdAt: string; expectedDate: string | null;
}

/**
 * Órdenes de compra: lista las órdenes pendientes y permite convertirlas en recepción (lo que
 * afecta inventario y cuentas por pagar). La captura de una nueva orden se hace en Compras.
 */
export function PurchaseOrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    api.get<Order[]>('/purchasing/purchases', { params: { docType: 'ORDER' } })
      .then((r) => setOrders(r.data))
      .catch(() => setOrders([]))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  const receive = async (id: number) => {
    try {
      await api.post(`/purchasing/orders/${id}/receive`, {});
      toast.success('Orden recibida', 'Se afectó inventario y cuentas por pagar.');
      load();
    } catch { toast.error('No se pudo recibir la orden'); }
  };

  return (
    <div>
      <div className="page-head-row">
        <div>
          <h1 className="page-title">Órdenes de compra</h1>
          <p className="page-sub">Solicitudes de mercancía pendientes de recibir</p>
        </div>
        <button className="btn-accent" onClick={() => navigate('/purchases')}>
          <FileText size={16} /> Nueva orden (en Compras)
        </button>
      </div>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <table className="ret-table">
          <thead>
            <tr>
              <th>Folio</th><th>Proveedor</th><th>Factura</th><th>Entrega</th>
              <th className="ta-right">Total</th><th>Estado</th><th className="ta-right">Acción</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((o) => (
              <tr key={o.id}>
                <td><strong>{o.folio}</strong></td>
                <td>{o.supplierName}</td>
                <td>{o.invoiceRef || <span className="muted">—</span>}</td>
                <td>{o.expectedDate || <span className="muted">—</span>}</td>
                <td className="ta-right">{money(o.total)}</td>
                <td>
                  <span className={`badge ${o.status === 'ORDERED' ? 'badge-warning' : 'badge-success'}`}>
                    {o.status === 'ORDERED' ? 'Pendiente' : o.status === 'RECEIVED' ? 'Recibida' : 'Cancelada'}
                  </span>
                </td>
                <td className="ta-right">
                  {o.status === 'ORDERED' && (
                    <button className="btn-accent btn-sm" onClick={() => receive(o.id)}>
                      <PackageCheck size={14} /> Recibir
                    </button>
                  )}
                  {o.status === 'RECEIVED' && <Check size={16} color="var(--success)" />}
                </td>
              </tr>
            ))}
            {orders.length === 0 && (
              <tr><td colSpan={7} className="ret-empty">
                {loading ? 'Cargando…' : 'No hay órdenes de compra pendientes.'}
              </td></tr>
            )}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}
