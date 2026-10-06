import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { ReceiptText, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Receipt {
  id: number; series: string | null; folio: number | null; paidAmount: number;
  paymentForm: string; paymentDate: string; bank: string | null; operationNo: string | null;
  uuid: string | null; status: string; receiverName: string | null; receiverRfc: string | null;
}

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);

/** Consultar recibos de pago emitidos. */
export function InvoicingReceiptsPage() {
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [client, setClient] = useState('');

  const receipts = useQuery({
    queryKey: ['invoicing', 'payments', from, to, client],
    queryFn: async () => (await api.get<Receipt[]>('/invoicing/payments', { params: { from, to, client } })).data,
  });
  const rows = receipts.data ?? [];

  return (
    <div>
      <h1 className="page-title">Consultar recibos de pago</h1>
      <p className="page-sub">Recibos electrónicos de pago (Pagos 2.0) emitidos</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="inv-toolbar">
          <label className="field"><span>Fecha inicial</span><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="field"><span>Fecha final</span><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
          <label className="field" style={{ flex: 1, minWidth: 220 }}><span>Cliente</span>
            <div className="cust-search"><Search size={16} /><input value={client} onChange={(e) => setClient(e.target.value)} placeholder="RFC o nombre…" /></div></label>
        </div>
        <div className="inv-table-wrap">
          <table className="inv-table">
            <thead><tr><th>Recibo</th><th>Cliente</th><th>Fecha</th><th>Forma</th><th>Banco</th><th className="num">Monto</th><th>UUID</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.series ?? ''}-{r.folio ?? ''}</td>
                  <td>{r.receiverName ?? '—'}</td>
                  <td>{r.paymentDate}</td>
                  <td>{r.paymentForm}</td>
                  <td>{r.bank ?? '—'}</td>
                  <td className="num">{money(r.paidAmount)}</td>
                  <td style={{ fontSize: 11, color: 'var(--text-muted)' }}>{r.uuid ?? '—'}</td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={7}><div className="inv-empty"><ReceiptText size={26} /><p>Sin recibos en el rango seleccionado.</p></div></td></tr>}
            </tbody>
          </table>
        </div>
      </motion.div>
    </div>
  );
}
