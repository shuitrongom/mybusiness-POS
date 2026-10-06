import { useState } from 'react';
import { motion } from 'motion/react';
import { Landmark, Check } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';

const today = () => new Date().toISOString().slice(0, 10);

// Bancos de México donde el negocio puede depositar para reponer saldo.
const BANKS = ['BBVA', 'Banamex (Citibanamex)', 'Santander', 'Banorte', 'HSBC', 'Scotiabank',
  'Banco Azteca', 'Inbursa', 'BanBajío', 'Afirme', 'Banregio', 'STP / SPEI'];

/**
 * Reportar abono: el negocio informa un depósito bancario que hizo para reponer su saldo. Queda
 * pendiente de aprobación; al aprobarse suma al saldo prepagado. (Pantalla "Reporta depósitos".)
 */
export function PaymentDepositPage() {
  const [form, setForm] = useState({
    posId: '', name: '', email: '', bank: 'BBVA', account: '', reference: '',
    amount: '', payDate: today(), comments: '',
  });
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof form, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async () => {
    if (!form.name.trim() || !form.bank || !form.reference.trim() || !form.amount) {
      toast.info('Completa los campos obligatorios (*)'); return;
    }
    setBusy(true);
    try {
      await api.post('/payments/deposits', {
        posId: form.posId || null, name: form.name.trim(), email: form.email || null,
        bank: form.bank, account: form.account || null, reference: form.reference.trim(),
        amount: Number(form.amount), payDate: form.payDate, comments: form.comments || null,
      });
      toast.success('Abono reportado', 'Quedó pendiente de aprobación. Al aprobarse se suma a tu saldo.');
      setForm({ ...form, reference: '', amount: '', comments: '' });
    } catch { toast.error('No se pudo reportar el abono'); } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Reportar abono</h1>
      <p className="page-sub">Reporta tu depósito bancario para reponer tu saldo prepagado</p>

      <motion.div className="card" style={{ maxWidth: 620 }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Landmark size={18} /> Datos del depósito</h3>
        <div className="drawer-section" style={{ marginTop: 'var(--space-3)' }}>
          <div className="grid-2">
            <label className="field"><span>ID POS / terminal</span>
              <input value={form.posId} onChange={(e) => set('posId', e.target.value)} /></label>
            <label className="field"><span>Nombre *</span>
              <input value={form.name} onChange={(e) => set('name', e.target.value)} /></label>
          </div>
          <label className="field"><span>Correo</span>
            <input type="email" value={form.email} onChange={(e) => set('email', e.target.value)} /></label>
          <div className="grid-2">
            <label className="field"><span>Banco *</span>
              <select value={form.bank} onChange={(e) => set('bank', e.target.value)}>
                {BANKS.map((b) => <option key={b} value={b}>{b}</option>)}
              </select></label>
            <label className="field"><span>Cuenta</span>
              <input value={form.account} onChange={(e) => set('account', e.target.value)} /></label>
          </div>
          <div className="grid-2">
            <label className="field"><span>Referencia *</span>
              <input value={form.reference} onChange={(e) => set('reference', e.target.value)} /></label>
            <label className="field"><span>Importe *</span>
              <input type="number" step="0.01" value={form.amount} onChange={(e) => set('amount', e.target.value)} /></label>
          </div>
          <label className="field"><span>Fecha del pago *</span>
            <input type="date" value={form.payDate} onChange={(e) => set('payDate', e.target.value)} /></label>
          <label className="field"><span>Comentarios</span>
            <textarea rows={3} value={form.comments} onChange={(e) => set('comments', e.target.value)} /></label>
          <p className="drawer-note" style={{ marginTop: 0 }}>Los datos marcados con (*) son obligatorios.</p>
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button className="btn-accent" disabled={busy} onClick={submit}><Check size={16} /> {busy ? 'Enviando…' : 'Aplicar'}</button>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
