import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { HandCoins, Search, Plus, Check } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Pending {
  id: number; series: string | null; folio: number | null; uuid: string | null;
  total: number; paid: number; balance: number; currency: string;
  receiverName: string; receiverRfc: string;
}
interface FormaPago { clave: string; descripcion: string; }
interface Applied { cfdiId: number; label: string; balance: number; paidAmount: number; }

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);

/** Generar recibo de pago (complemento Pagos 2.0). */
export function InvoicingReceiptPage() {
  const [client, setClient] = useState('');
  const [paymentForm, setPaymentForm] = useState('03');
  const [paymentDate, setPaymentDate] = useState(today());
  const [bank, setBank] = useState('');
  const [operationNo, setOperationNo] = useState('');
  const [applied, setApplied] = useState<Applied[]>([]);
  const [busy, setBusy] = useState(false);

  const pending = useQuery({
    queryKey: ['invoicing', 'pending', client],
    queryFn: async () => (await api.get<Pending[]>('/invoicing/pending-for-payment', { params: { client } })).data,
  });
  const formas = useQuery({ queryKey: ['invoicing', 'sat', 'forma-pago'], queryFn: async () => (await api.get<FormaPago[]>('/invoicing/sat/forma-pago')).data });

  const total = useMemo(() => applied.reduce((a, d) => a + (Number(d.paidAmount) || 0), 0), [applied]);

  const add = (p: Pending) => {
    if (applied.some((a) => a.cfdiId === p.id)) return;
    setApplied((cur) => [...cur, { cfdiId: p.id, label: `${p.series ?? ''}-${p.folio ?? ''} · ${p.receiverName}`, balance: p.balance, paidAmount: p.balance }]);
  };
  const remove = (id: number) => setApplied((cur) => cur.filter((a) => a.cfdiId !== id));
  const setAmount = (id: number, v: string) => setApplied((cur) => cur.map((a) => a.cfdiId === id ? { ...a, paidAmount: Number(v) || 0 } : a));

  const generate = async () => {
    if (applied.length === 0) { toast.error('Sin documentos', 'Agrega al menos una factura a pagar.'); return; }
    setBusy(true);
    try {
      const { data } = await api.post('/invoicing/payment-receipts', {
        customerId: null, paymentForm, paymentDate, bank, operationNo,
        docs: applied.map((a) => ({ cfdiId: a.cfdiId, installment: 1, paidAmount: a.paidAmount })),
      });
      toast.success('Recibo generado', `Folio ${data.series}-${data.folio} · ${money(data.totalPaid)}.`);
      setApplied([]); setBank(''); setOperationNo('');
    } catch {
      toast.error('No se pudo generar', 'Revisa los documentos y montos.');
    } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Generar recibo de pago</h1>
      <p className="page-sub">Complemento de recepción de pagos CFDI 4.0 (Pagos 2.0)</p>

      <div className="inv-grid2" style={{ alignItems: 'start' }}>
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><HandCoins size={18} /> Datos del pago</h3>
          <div className="inv-grid2" style={{ marginTop: 'var(--space-3)' }}>
            <label className="field"><span>Forma de pago</span>
              <select value={paymentForm} onChange={(e) => setPaymentForm(e.target.value)}>
                {(formas.data ?? []).map((f) => <option key={f.clave} value={f.clave}>{f.clave} — {f.descripcion}</option>)}
              </select></label>
            <label className="field"><span>Fecha del pago</span><input type="date" value={paymentDate} onChange={(e) => setPaymentDate(e.target.value)} /></label>
            <label className="field"><span>Banco</span><input value={bank} onChange={(e) => setBank(e.target.value)} /></label>
            <label className="field"><span>No. de operación</span><input value={operationNo} onChange={(e) => setOperationNo(e.target.value)} /></label>
          </div>

          <h3 className="sec-title" style={{ marginTop: 'var(--space-4)' }}>Documentos para aplicar pago</h3>
          <div className="inv-table-wrap" style={{ marginTop: 'var(--space-2)' }}>
            <table className="inv-table">
              <thead><tr><th>Documento</th><th className="num">Saldo</th><th className="num">Monto a pagar</th><th></th></tr></thead>
              <tbody>
                {applied.map((a) => (
                  <tr key={a.cfdiId}>
                    <td>{a.label}</td>
                    <td className="num">{money(a.balance)}</td>
                    <td className="num"><input type="number" step="0.01" value={a.paidAmount} onChange={(e) => setAmount(a.cfdiId, e.target.value)} style={{ width: 120, textAlign: 'right' }} /></td>
                    <td><button className="btn-ghost" onClick={() => remove(a.cfdiId)}>Quitar</button></td>
                  </tr>
                ))}
                {applied.length === 0 && <tr><td colSpan={4}><div className="inv-empty">Agrega documentos de la derecha.</div></td></tr>}
              </tbody>
            </table>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--space-4)' }}>
            <span className="inv-chip">Total del recibo: <strong>{money(total)}</strong></span>
            <button className="btn-accent" disabled={busy || applied.length === 0} onClick={generate}><Check size={16} /> {busy ? 'Generando…' : 'Generar recibo'}</button>
          </div>
        </motion.div>

        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title">Documentos pendientes de pago</h3>
          <div className="cust-search" style={{ margin: 'var(--space-3) 0' }}>
            <Search size={16} /><input value={client} onChange={(e) => setClient(e.target.value)} placeholder="Buscar cliente (RFC o nombre)…" />
          </div>
          <div className="inv-table-wrap">
            <table className="inv-table">
              <thead><tr><th>Doc.</th><th>Cliente</th><th className="num">Saldo</th><th></th></tr></thead>
              <tbody>
                {(pending.data ?? []).map((p) => (
                  <tr key={p.id}>
                    <td>{p.series ?? ''}-{p.folio ?? ''}</td>
                    <td>{p.receiverName}</td>
                    <td className="num">{money(p.balance)}</td>
                    <td><button className="btn-ghost" onClick={() => add(p)}><Plus size={14} /> Agregar</button></td>
                  </tr>
                ))}
                {(pending.data ?? []).length === 0 && <tr><td colSpan={4}><div className="inv-empty">No hay facturas a crédito (PPD) con saldo pendiente.</div></td></tr>}
              </tbody>
            </table>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
