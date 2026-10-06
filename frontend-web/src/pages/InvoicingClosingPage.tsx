import { useState } from 'react';
import { motion } from 'motion/react';
import { FileCheck2, Calculator, Stamp } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Closing {
  tickets: number; remissions: number; returns: number; net: number;
  invoice: { cfdiId: number; status: string; uuid: string | null };
}

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);

/** Factura de cierre (global del periodo al público en general). */
export function InvoicingClosingPage() {
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [result, setResult] = useState<Closing | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async () => {
    setBusy(true);
    try {
      const { data } = await api.post<Closing>('/invoicing/closing', { from, to });
      setResult(data);
      if (data.invoice.status === 'STAMPED') toast.success('Factura de cierre timbrada', `UUID: ${data.invoice.uuid}`);
      else toast.info('Factura de cierre', `Estado: ${data.invoice.status}`);
    } catch {
      toast.error('No se pudo generar', 'Revisa el periodo seleccionado.');
    } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Factura de cierre</h1>
      <p className="page-sub">Agrupa tickets y remisiones del periodo (menos devoluciones) en un CFDI global al público en general</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><FileCheck2 size={18} /> Periodo</h3>
        <div className="inv-toolbar" style={{ marginTop: 'var(--space-3)' }}>
          <label className="field"><span>Fecha inicial</span><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="field"><span>Fecha final</span><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
          <button className="btn-ghost" onClick={run} disabled={busy}><Calculator size={16} /> {busy ? 'Calculando…' : 'Calcular y timbrar'}</button>
        </div>

        {result && (
          <div className="inv-grid4" style={{ marginTop: 'var(--space-4)' }}>
            <div className="inv-chip">Tickets: <strong>{money(result.tickets)}</strong></div>
            <div className="inv-chip">Remisiones: <strong>{money(result.remissions)}</strong></div>
            <div className="inv-chip">Devoluciones: <strong>-{money(result.returns)}</strong></div>
            <div className="inv-chip" style={{ background: 'color-mix(in srgb, var(--success) 14%, transparent)', color: 'var(--success)' }}>
              <Stamp size={15} /> Neto facturado: <strong>{money(result.net)}</strong>
            </div>
          </div>
        )}
        {result?.invoice.uuid && (
          <p style={{ marginTop: 'var(--space-3)' }} className="page-sub">UUID de la factura global: <strong>{result.invoice.uuid}</strong></p>
        )}
      </motion.div>
    </div>
  );
}
