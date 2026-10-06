import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Users, Banknote, CreditCard } from 'lucide-react';
import { api } from '@/lib/api';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Row { cashier: string; salesCount: number; total: number; cash: number; card: number; transfer: number; }

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);

/** Reporte de ventas por cajero: el admin ve la venta de cada cajero por separado en un rango. */
export function SalesByCashierPage() {
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());

  const report = useQuery({
    queryKey: ['sales-by-cashier', from, to],
    queryFn: async () => (await api.get<Row[]>('/shifts/sales-by-cashier', { params: { from, to } })).data,
  });
  const rows = report.data ?? [];
  const grand = rows.reduce((a, r) => a + (Number(r.total) || 0), 0);
  const grandCount = rows.reduce((a, r) => a + (Number(r.salesCount) || 0), 0);

  return (
    <div>
      <h1 className="page-title">Ventas por cajero</h1>
      <p className="page-sub">Consulta la venta de cada cajero por separado en el periodo elegido</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="inv-toolbar">
          <label className="field"><span>Fecha inicial</span><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="field"><span>Fecha final</span><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
          <div className="inv-chip" style={{ marginLeft: 'auto' }}>Total del periodo: <strong>{money(grand)}</strong> · {grandCount} ventas</div>
        </div>

        <div className="inv-table-wrap">
          <table className="inv-table">
            <thead>
              <tr><th>Cajero</th><th className="num"># Ventas</th><th className="num"><Banknote size={13} /> Efectivo</th><th className="num"><CreditCard size={13} /> Tarjeta</th><th className="num">Transfer.</th><th className="num">Total</th></tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.cashier}>
                  <td>{r.cashier}</td>
                  <td className="num">{r.salesCount}</td>
                  <td className="num">{money(r.cash)}</td>
                  <td className="num">{money(r.card)}</td>
                  <td className="num">{money(r.transfer)}</td>
                  <td className="num"><strong>{money(r.total)}</strong></td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={6}><div className="inv-empty"><Users size={26} /><p>Sin ventas en el periodo.</p></div></td></tr>}
            </tbody>
          </table>
        </div>
      </motion.div>
    </div>
  );
}
