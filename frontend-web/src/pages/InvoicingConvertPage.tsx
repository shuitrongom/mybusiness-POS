import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { FileStack, Ticket, Check } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Doc { id: number; folio: string | null; customerName?: string | null; subtotal: number; tax: number; total: number; createdAt: string; }
interface Regime { clave: string; descripcion: string; }
interface Uso { clave: string; descripcion: string; }

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);

/**
 * Convierte remisiones o tickets (ventas) en factura. Reutilizable: `mode` decide la fuente y el
 * endpoint de conversión.
 */
export function InvoicingConvertPage({ mode }: { mode: 'remission' | 'ticket' }) {
  const isRemission = mode === 'remission';
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [selected, setSelected] = useState<Doc | null>(null);
  const [rfc, setRfc] = useState('');
  const [name, setName] = useState('');
  const [zip, setZip] = useState('');
  const [regime, setRegime] = useState('');
  const [uso, setUso] = useState('G03');
  const [busy, setBusy] = useState(false);

  const list = useQuery({
    queryKey: ['invoicing', mode, from, to],
    queryFn: async () => isRemission
      ? (await api.get<Doc[]>('/invoicing/remissions')).data
      : (await api.get<Doc[]>('/invoicing/uninvoiced-sales', { params: { from, to } })).data,
  });
  const regimes = useQuery({ queryKey: ['invoicing', 'sat', 'regime'], queryFn: async () => (await api.get<Regime[]>('/invoicing/sat/regime')).data });
  const usos = useQuery({ queryKey: ['invoicing', 'sat', 'uso'], queryFn: async () => (await api.get<Uso[]>('/invoicing/sat/uso-cfdi')).data });

  const rows = list.data ?? [];
  const canInvoice = selected != null && rfc.trim() !== '' && name.trim() !== '' && zip.trim() !== '' && regime !== '';

  const invoice = async () => {
    if (!selected || !canInvoice) return;
    setBusy(true);
    try {
      const url = isRemission ? `/invoicing/remissions/${selected.id}/to-invoice` : `/invoicing/sales/${selected.id}/to-invoice`;
      const { data } = await api.post(url, { receiverRfc: rfc.trim(), receiverName: name.trim(), receiverZip: zip.trim(), receiverRegime: regime, cfdiUse: uso });
      toast.success('Factura generada', data.uuid ? `UUID: ${data.uuid}` : `Estado: ${data.status}`);
      setSelected(null); setRfc(''); setName(''); setZip('');
      list.refetch();
    } catch {
      toast.error('No se pudo facturar', 'Revisa los datos fiscales del cliente.');
    } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">{isRemission ? 'Remisiones a factura' : 'Tickets a factura'}</h1>
      <p className="page-sub">{isRemission ? 'Convierte tus remisiones (notas de venta) en CFDI 4.0' : 'Convierte ventas del punto de venta en CFDI 4.0'}</p>

      <div className="inv-grid2" style={{ alignItems: 'start' }}>
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title">{isRemission ? <FileStack size={18} /> : <Ticket size={18} />} {isRemission ? 'Remisiones abiertas' : 'Ventas sin facturar'}</h3>
          {!isRemission && (
            <div className="inv-toolbar" style={{ marginTop: 'var(--space-3)' }}>
              <label className="field"><span>Desde</span><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
              <label className="field"><span>Hasta</span><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
            </div>
          )}
          <div className="inv-table-wrap" style={{ marginTop: 'var(--space-2)' }}>
            <table className="inv-table">
              <thead><tr><th>Folio</th><th>Fecha</th><th className="num">Subtotal</th><th className="num">Total</th></tr></thead>
              <tbody>
                {rows.map((d) => (
                  <tr key={d.id} className={selected?.id === d.id ? 'inv-selrow' : ''} style={{ cursor: 'pointer' }} onClick={() => setSelected(d)}>
                    <td>{d.folio ?? d.id}</td>
                    <td>{d.createdAt?.slice(0, 10)}</td>
                    <td className="num">{money(d.subtotal)}</td>
                    <td className="num">{money(d.total)}</td>
                  </tr>
                ))}
                {rows.length === 0 && <tr><td colSpan={4}><div className="inv-empty">No hay documentos por facturar.</div></td></tr>}
              </tbody>
            </table>
          </div>
        </motion.div>

        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title">Datos fiscales del cliente</h3>
          {!selected ? (
            <div className="inv-empty">Selecciona un documento de la izquierda.</div>
          ) : (
            <>
              <p className="inv-chip" style={{ marginBottom: 'var(--space-3)' }}>Documento: <strong>{selected.folio ?? selected.id}</strong> · {money(selected.total)}</p>
              <div className="inv-grid2">
                <label className="field"><span>RFC *</span><input value={rfc} onChange={(e) => setRfc(e.target.value.toUpperCase())} maxLength={13} /></label>
                <label className="field"><span>CP fiscal *</span><input value={zip} onChange={(e) => setZip(e.target.value)} maxLength={5} /></label>
                <label className="field" style={{ gridColumn: 'span 2' }}><span>Nombre / Razón social *</span><input value={name} onChange={(e) => setName(e.target.value)} /></label>
                <label className="field"><span>Régimen fiscal *</span>
                  <select value={regime} onChange={(e) => setRegime(e.target.value)}>
                    <option value="">Selecciona…</option>
                    {(regimes.data ?? []).map((r) => <option key={r.clave} value={r.clave}>{r.clave} — {r.descripcion}</option>)}
                  </select></label>
                <label className="field"><span>Uso CFDI *</span>
                  <select value={uso} onChange={(e) => setUso(e.target.value)}>
                    {(usos.data ?? []).map((u) => <option key={u.clave} value={u.clave}>{u.clave} — {u.descripcion}</option>)}
                  </select></label>
              </div>
              <button className="btn-accent" style={{ marginTop: 'var(--space-4)' }} disabled={!canInvoice || busy} onClick={invoice}>
                <Check size={16} /> {busy ? 'Facturando…' : 'Generar factura'}
              </button>
            </>
          )}
        </motion.div>
      </div>
    </div>
  );
}

export function InvoicingRemissionsPage() { return <InvoicingConvertPage mode="remission" />; }
export function InvoicingTicketsPage() { return <InvoicingConvertPage mode="ticket" />; }
