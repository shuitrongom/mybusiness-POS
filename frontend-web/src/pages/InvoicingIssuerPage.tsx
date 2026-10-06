import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Building2, Save, FlaskConical } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Issuer {
  legalName?: string; rfc?: string; taxRegime?: string;
  street?: string; extNumber?: string; intNumber?: string; neighborhood?: string;
  locality?: string; municipality?: string; state?: string; zipCode?: string;
  seriesInvoice?: string; seriesCredit?: string; seriesPayroll?: string;
  seriesPayment?: string; seriesTransfer?: string;
  testMode?: boolean; pacProvider?: string; pacUser?: string; pacWsUrl?: string; decimals?: number;
}
interface Regime { clave: string; descripcion: string; }

/** Datos fiscales del emisor (pantalla "Datos para factura Electrónica"). */
export function InvoicingIssuerPage() {
  const [form, setForm] = useState<Issuer>({ testMode: true, decimals: 2 });

  const issuer = useQuery({ queryKey: ['invoicing', 'issuer'], queryFn: async () => (await api.get<Issuer>('/invoicing/issuer')).data });
  const regimes = useQuery({ queryKey: ['invoicing', 'sat', 'regime'], queryFn: async () => (await api.get<Regime[]>('/invoicing/sat/regime')).data });

  useEffect(() => { if (issuer.data) setForm((f) => ({ ...f, ...issuer.data })); }, [issuer.data]);

  const set = (k: keyof Issuer, v: string | boolean | number) => setForm((f) => ({ ...f, [k]: v }));

  const save = async () => {
    try {
      await api.put('/invoicing/issuer', form);
      toast.success('Datos guardados', 'La información fiscal del emisor se actualizó.');
    } catch {
      toast.error('No se pudo guardar', 'Revisa los datos e inténtalo de nuevo.');
    }
  };

  return (
    <div>
      <h1 className="page-title">Datos para factura electrónica</h1>
      <p className="page-sub">Datos fiscales del emisor de tus CFDI 4.0 y configuración de series</p>

      {form.testMode && (
        <div className="inv-test-banner"><FlaskConical size={16} /> Modo prueba activo: los comprobantes se timbran en sandbox (no válidos ante el SAT).</div>
      )}

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Building2 size={18} /> Identidad fiscal</h3>
        <div className="inv-grid3" style={{ marginTop: 'var(--space-3)' }}>
          <label className="field" style={{ gridColumn: 'span 2' }}><span>Nombre / Razón social *</span>
            <input value={form.legalName ?? ''} onChange={(e) => set('legalName', e.target.value)} /></label>
          <label className="field"><span>RFC *</span>
            <input value={form.rfc ?? ''} onChange={(e) => set('rfc', e.target.value.toUpperCase())} maxLength={13} /></label>
          <label className="field"><span>Régimen fiscal *</span>
            <select value={form.taxRegime ?? ''} onChange={(e) => set('taxRegime', e.target.value)}>
              <option value="">Selecciona…</option>
              {(regimes.data ?? []).map((r) => <option key={r.clave} value={r.clave}>{r.clave} — {r.descripcion}</option>)}
            </select></label>
          <label className="field"><span>Código postal (lugar de expedición) *</span>
            <input value={form.zipCode ?? ''} onChange={(e) => set('zipCode', e.target.value)} maxLength={5} /></label>
          <label className="field"><span>Decimales</span>
            <input type="number" min={0} max={6} value={form.decimals ?? 2} onChange={(e) => set('decimals', Number(e.target.value))} /></label>
        </div>

        <h3 className="sec-title" style={{ marginTop: 'var(--space-4)' }}>Domicilio</h3>
        <div className="inv-grid4" style={{ marginTop: 'var(--space-3)' }}>
          <label className="field" style={{ gridColumn: 'span 2' }}><span>Calle</span>
            <input value={form.street ?? ''} onChange={(e) => set('street', e.target.value)} /></label>
          <label className="field"><span>No. exterior</span>
            <input value={form.extNumber ?? ''} onChange={(e) => set('extNumber', e.target.value)} /></label>
          <label className="field"><span>No. interior</span>
            <input value={form.intNumber ?? ''} onChange={(e) => set('intNumber', e.target.value)} /></label>
          <label className="field"><span>Colonia</span>
            <input value={form.neighborhood ?? ''} onChange={(e) => set('neighborhood', e.target.value)} /></label>
          <label className="field"><span>Localidad</span>
            <input value={form.locality ?? ''} onChange={(e) => set('locality', e.target.value)} /></label>
          <label className="field"><span>Municipio</span>
            <input value={form.municipality ?? ''} onChange={(e) => set('municipality', e.target.value)} /></label>
          <label className="field"><span>Estado</span>
            <input value={form.state ?? ''} onChange={(e) => set('state', e.target.value)} /></label>
        </div>

        <h3 className="sec-title" style={{ marginTop: 'var(--space-4)' }}>Series</h3>
        <div className="inv-grid4" style={{ marginTop: 'var(--space-3)' }}>
          <label className="field"><span>Facturas</span>
            <input value={form.seriesInvoice ?? ''} onChange={(e) => set('seriesInvoice', e.target.value)} /></label>
          <label className="field"><span>Notas de crédito</span>
            <input value={form.seriesCredit ?? ''} onChange={(e) => set('seriesCredit', e.target.value)} /></label>
          <label className="field"><span>Nóminas</span>
            <input value={form.seriesPayroll ?? ''} onChange={(e) => set('seriesPayroll', e.target.value)} /></label>
          <label className="field"><span>Pagos</span>
            <input value={form.seriesPayment ?? ''} onChange={(e) => set('seriesPayment', e.target.value)} /></label>
          <label className="field"><span>Traslados</span>
            <input value={form.seriesTransfer ?? ''} onChange={(e) => set('seriesTransfer', e.target.value)} /></label>
        </div>

        <h3 className="sec-title" style={{ marginTop: 'var(--space-4)' }}>Timbrado (PAC)</h3>
        <div className="inv-grid3" style={{ marginTop: 'var(--space-3)' }}>
          <label className="field"><span>Proveedor PAC</span>
            <input value={form.pacProvider ?? ''} onChange={(e) => set('pacProvider', e.target.value)} placeholder="Finkok, Facturama…" /></label>
          <label className="field"><span>Usuario PAC</span>
            <input value={form.pacUser ?? ''} onChange={(e) => set('pacUser', e.target.value)} /></label>
          <label className="field"><span>URL web service</span>
            <input value={form.pacWsUrl ?? ''} onChange={(e) => set('pacWsUrl', e.target.value)} /></label>
          <label className="field" style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 22 }}>
            <input type="checkbox" checked={form.testMode ?? true} onChange={(e) => set('testMode', e.target.checked)} style={{ width: 18, height: 18 }} />
            <span style={{ margin: 0 }}>Modo prueba (sandbox)</span></label>
        </div>

        <div style={{ marginTop: 'var(--space-4)' }}>
          <button className="btn-accent" onClick={save}><Save size={16} /> Guardar datos fiscales</button>
        </div>
      </motion.div>
    </div>
  );
}
