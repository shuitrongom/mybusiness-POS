import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { motion } from 'motion/react';
import { FileMinus, FileText, Receipt } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { money } from '@/lib/format';
import { fadeInUp, pressable } from '@/lib/motion';
import '@/pages/dashboard.css';

interface InvoiceResult {
  cfdiId: number;
  status: string;
  uuid: string | null;
  duplicated: boolean;
}

/** Extrae un mensaje de error legible de una respuesta de axios. */
function errorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as { error?: string; message?: string } | undefined;
    return data?.error ?? data?.message ?? err.message;
  }
  return 'Ocurrió un error inesperado.';
}

/**
 * Facturación CFDI 4.0: emisión de factura con los datos fiscales del receptor y un concepto
 * simple, y cancelación por id de CFDI. Genera una llave de idempotencia por emisión.
 */
export function InvoicingPage() {
  const [form, setForm] = useState({
    receiverRfc: '',
    receiverName: '',
    receiverZip: '',
    receiverRegime: '',
    cfdiUse: '',
    description: '',
    satProdServ: '01010101',
    satUnit: 'H87',
    quantity: '1',
    unitPrice: '',
  });
  const [result, setResult] = useState<InvoiceResult | null>(null);
  const [emitError, setEmitError] = useState<string | null>(null);

  const [cancelId, setCancelId] = useState('');
  const [cancelMsg, setCancelMsg] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const amount = (Number(form.quantity) || 0) * (Number(form.unitPrice) || 0);

  const emit = useMutation({
    mutationFn: async () =>
      (await api.post<InvoiceResult>('/invoicing/invoices', {
        saleId: null,
        receiverRfc: form.receiverRfc,
        receiverName: form.receiverName,
        receiverZip: form.receiverZip,
        receiverRegime: form.receiverRegime,
        cfdiUse: form.cfdiUse,
        concepts: [
          {
            satProdServ: form.satProdServ,
            satUnit: form.satUnit,
            description: form.description,
            quantity: Number(form.quantity),
            unitPrice: Number(form.unitPrice),
            amount,
          },
        ],
        idempotencyKey: crypto.randomUUID(),
      })).data,
    onSuccess: (data) => {
      setResult(data);
      setEmitError(null);
      if (data.duplicated) {
        toast.info('Factura ya emitida', 'Esta factura ya existía; se reutilizó la anterior.');
      } else if (data.status === 'STAMPED') {
        toast.success('Factura timbrada', `UUID: ${data.uuid ?? '—'}.`);
      } else {
        toast.success('Factura emitida', `Estado: ${data.status}.`);
      }
    },
    onError: (err) => {
      setResult(null);
      setEmitError(errorMessage(err));
      toast.error('No se pudo emitir la factura', errorMessage(err));
    },
  });

  const cancel = useMutation({
    mutationFn: async () => api.post(`/invoicing/invoices/${Number(cancelId)}/cancel`),
    onSuccess: () => {
      setCancelMsg(`CFDI ${cancelId} cancelado correctamente.`);
      setCancelError(null);
      toast.success('Factura cancelada', `CFDI ${cancelId} cancelado ante el SAT.`);
    },
    onError: (err) => {
      setCancelMsg(null);
      setCancelError(errorMessage(err));
      toast.error('No se pudo cancelar la factura', errorMessage(err));
    },
  });

  return (
    <div>
      <h1 className="page-title">Facturación</h1>
      <p className="page-sub">Emisión y cancelación de CFDI 4.0</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><FileText size={18} /> Emitir factura</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr 1fr 1fr', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>RFC receptor</span>
            <input value={form.receiverRfc} onChange={(e) => setForm({ ...form, receiverRfc: e.target.value })} /></label>
          <label className="field"><span>Nombre / Razón social</span>
            <input value={form.receiverName} onChange={(e) => setForm({ ...form, receiverName: e.target.value })} /></label>
          <label className="field"><span>Código postal</span>
            <input value={form.receiverZip} onChange={(e) => setForm({ ...form, receiverZip: e.target.value })} /></label>
          <label className="field"><span>Régimen fiscal</span>
            <input value={form.receiverRegime} onChange={(e) => setForm({ ...form, receiverRegime: e.target.value })} placeholder="601" /></label>
          <label className="field"><span>Uso CFDI</span>
            <input value={form.cfdiUse} onChange={(e) => setForm({ ...form, cfdiUse: e.target.value })} placeholder="G03" /></label>
        </div>

        <h3 className="sec-title" style={{ marginTop: 'var(--space-4)' }}><Receipt size={18} /> Concepto</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Descripción</span>
            <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
          <label className="field"><span>Clave SAT prod/serv</span>
            <input value={form.satProdServ} onChange={(e) => setForm({ ...form, satProdServ: e.target.value })} /></label>
          <label className="field"><span>Clave unidad</span>
            <input value={form.satUnit} onChange={(e) => setForm({ ...form, satUnit: e.target.value })} /></label>
          <label className="field"><span>Cantidad</span>
            <input type="number" step="0.01" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} /></label>
          <label className="field"><span>Precio unitario</span>
            <input type="number" step="0.01" value={form.unitPrice} onChange={(e) => setForm({ ...form, unitPrice: e.target.value })} /></label>
          <motion.button className="btn-accent" disabled={!form.receiverRfc || !form.description || !form.unitPrice || emit.isPending}
                  onClick={() => emit.mutate()}
                  whileHover={!form.receiverRfc || !form.description || !form.unitPrice || emit.isPending ? undefined : pressable.whileHover}
                  whileTap={!form.receiverRfc || !form.description || !form.unitPrice || emit.isPending ? undefined : pressable.whileTap}
                  transition={pressable.transition}>
            {emit.isPending ? 'Emitiendo…' : 'Emitir'}
          </motion.button>
        </div>
        <p className="page-sub" style={{ margin: 'var(--space-3) 0 0' }}>
          Importe del concepto: <strong>{money(amount)}</strong>
        </p>
        {result && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className={`badge ${result.status === 'STAMPED' ? 'badge-success' : 'badge-warning'}`}>
              {result.status === 'STAMPED' ? 'Timbrada' : result.status}
            </span>{' '}
            UUID: <strong>{result.uuid ?? '—'}</strong>{' · '}CFDI id: <strong>{result.cfdiId}</strong>
            {result.duplicated && <> {' · '}<span className="badge badge-muted">Duplicada</span></>}
          </p>
        )}
        {emitError && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-danger">Error</span> {emitError}
          </p>
        )}
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-5)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><FileMinus size={18} /> Cancelar factura</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end', maxWidth: 420 }}>
          <label className="field"><span>CFDI (id)</span>
            <input value={cancelId} onChange={(e) => setCancelId(e.target.value)} /></label>
          <motion.button className="btn-ghost" disabled={!cancelId || cancel.isPending}
                  onClick={() => cancel.mutate()}
                  whileHover={!cancelId || cancel.isPending ? undefined : pressable.whileHover}
                  whileTap={!cancelId || cancel.isPending ? undefined : pressable.whileTap}
                  transition={pressable.transition}>
            {cancel.isPending ? 'Cancelando…' : 'Cancelar'}
          </motion.button>
        </div>
        {cancelMsg && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-success">Cancelada</span> {cancelMsg}
          </p>
        )}
        {cancelError && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-danger">Error</span> {cancelError}
          </p>
        )}
      </motion.div>
    </div>
  );
}
