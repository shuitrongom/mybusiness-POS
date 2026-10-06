import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Gauge } from 'lucide-react';
import { api } from '@/lib/api';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/inventory.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);
const firstOfMonth = () => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10); };

interface QualityRow {
  classification: string; items: number; inventoryValue: number;
  costOfSales: number; sales: number; profit: number;
}

const CLASS_TONE: Record<string, string> = {
  'Alto': 'q-alto', 'Medio': 'q-medio', 'Bajo': 'q-bajo', 'Lento': 'q-lento',
  'Nuevo': 'q-nuevo', 'Sin movimiento': 'q-sinmov',
};

/**
 * Calidad de inventario: clasificación ABC de los productos según su venta y valor en un rango de
 * fechas. Alto / Medio / Bajo / Lento / Nuevo / Sin movimiento, con valor de inventario, costo de
 * ventas, venta y utilidad por clasificación.
 */
export function InventoryQualityPage() {
  const [from, setFrom] = useState(firstOfMonth());
  const [to, setTo] = useState(today());

  const { data: rows = [], isFetching, refetch } = useQuery({
    queryKey: ['inventory-quality', from, to],
    queryFn: async () => (await api.get<QualityRow[]>('/inventory/quality', { params: { from, to } })).data,
  });

  const totals = rows.reduce((acc, r) => ({
    items: acc.items + r.items, inventoryValue: acc.inventoryValue + r.inventoryValue,
    costOfSales: acc.costOfSales + r.costOfSales, sales: acc.sales + r.sales, profit: acc.profit + r.profit,
  }), { items: 0, inventoryValue: 0, costOfSales: 0, sales: 0, profit: 0 });

  const pct = (n: number, total: number) => total > 0 ? `${((n / total) * 100).toFixed(1)}%` : '—';

  return (
    <div>
      <h1 className="page-title">Calidad de inventario</h1>
      <p className="page-sub">Clasificación ABC de tu inventario por venta y valor</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="sec-title-row">
          <h3 className="sec-title"><Gauge size={18} /> Clasificación</h3>
          <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
            <span className="muted">Ventas del</span>
            <input type="date" className="pur-in" style={{ width: 150 }} value={from} onChange={(e) => setFrom(e.target.value)} />
            <span className="muted">al</span>
            <input type="date" className="pur-in" style={{ width: 150 }} value={to} onChange={(e) => setTo(e.target.value)} />
            <button className="btn-primary" onClick={() => refetch()} disabled={isFetching}>
              {isFetching ? 'Calculando…' : 'Calcular'}
            </button>
          </div>
        </div>

        <table className="ret-table q-table" style={{ marginTop: 'var(--space-3)' }}>
          <thead>
            <tr>
              <th>Clasificación</th><th className="ta-right">#Items</th><th className="ta-right">%</th>
              <th className="ta-right">Valor inventario</th><th className="ta-right">Costo de ventas</th>
              <th className="ta-right">Venta</th><th className="ta-right">Utilidad</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.classification}>
                <td><span className={`q-tag ${CLASS_TONE[r.classification] ?? ''}`}>{r.classification}</span></td>
                <td className="ta-right">{r.items}</td>
                <td className="ta-right">{pct(r.items, totals.items)}</td>
                <td className="ta-right">{money(r.inventoryValue)}</td>
                <td className="ta-right">{money(r.costOfSales)}</td>
                <td className="ta-right">{money(r.sales)}</td>
                <td className="ta-right">{money(r.profit)}</td>
              </tr>
            ))}
            <tr className="q-sum">
              <td><strong>Suma</strong></td>
              <td className="ta-right"><strong>{totals.items}</strong></td>
              <td className="ta-right">100%</td>
              <td className="ta-right"><strong>{money(totals.inventoryValue)}</strong></td>
              <td className="ta-right"><strong>{money(totals.costOfSales)}</strong></td>
              <td className="ta-right"><strong>{money(totals.sales)}</strong></td>
              <td className="ta-right"><strong>{money(totals.profit)}</strong></td>
            </tr>
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}
