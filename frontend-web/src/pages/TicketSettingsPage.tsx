import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Receipt, Save, Camera } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fileToThumbnailDataUrl } from '@/lib/image';
import { fadeInUp, pressable } from '@/lib/motion';
import { Ticket, defaultTicketSettings, type TicketSettings, type TicketData } from '@/components/Ticket';
import { printTicketHtml, setPaperWidth } from '@/lib/thermalPrint';
import { saleTicketHtml } from '@/lib/ticketHtml';
import { Printer } from 'lucide-react';
import './dashboard.css';
import './ticket-settings.css';

/** Venta de ejemplo para la vista previa del ticket. */
const SAMPLE: TicketData = {
  folio: 'V-1024',
  branchName: 'Matriz',
  cashier: 'María López',
  dateTime: new Date(),
  lines: [
    { description: 'Concha de vainilla', quantity: 3, unitPrice: 15, lineTotal: 45 },
    { description: 'Bolillo', quantity: 6, unitPrice: 3, lineTotal: 18 },
    { description: 'Café de olla', quantity: 1, unitPrice: 25, lineTotal: 25 },
  ],
  total: 88,
  methodLabel: 'Efectivo',
  received: 100,
  change: 12,
};

/**
 * Configuración del ticket: el negocio edita qué aparece en su comprobante y ve una VISTA PREVIA
 * en vivo (mismo componente que se imprime). Guarda vía PUT /tickets/settings.
 */
export function TicketSettingsPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<TicketSettings>(defaultTicketSettings);

  const settings = useQuery({
    queryKey: ['ticket', 'settings'],
    queryFn: async () => (await api.get<TicketSettings>('/tickets/settings')).data,
  });

  useEffect(() => {
    if (settings.data) setForm(settings.data);
  }, [settings.data]);

  const save = useMutation({
    mutationFn: async () => (await api.put('/tickets/settings', form)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ticket', 'settings'] });
      toast.success('Ticket actualizado', 'La nueva configuración se usará en las próximas ventas.');
    },
    onError: () => toast.error('No se pudo guardar la configuración del ticket'),
  });

  const set = <K extends keyof TicketSettings>(key: K, value: TicketSettings[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const onPickLogo = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      set('logoUrl', await fileToThumbnailDataUrl(file));
    } catch {
      toast.error('No se pudo procesar el logo', 'Prueba con otra imagen.');
    }
  };

  return (
    <div>
      <div className="sec-title"><Receipt size={20} /><h1 className="page-title">Ticket</h1></div>
      <p className="page-sub">Personaliza qué aparece en tu comprobante de venta</p>

      <div className="tk-layout">
        {/* Formulario */}
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3>Datos del negocio</h3>
          <div className="tk-photo-row">
            <label className="tk-logo-drop">
              {form.logoUrl ? <img src={form.logoUrl} alt="Logo" className="tk-logo-preview" />
                : <span className="tk-logo-ph"><Camera size={22} />Logo</span>}
              <input type="file" accept="image/*" onChange={onPickLogo} hidden />
            </label>
            {form.logoUrl && <button className="btn-ghost" onClick={() => set('logoUrl', null)}>Quitar logo</button>}
          </div>

          <label className="field">
            <span>Nombre comercial</span>
            <input value={form.businessName ?? ''} onChange={(e) => set('businessName', e.target.value)}
              placeholder="Panadería Silva" />
          </label>
          <label className="field">
            <span>Dirección</span>
            <input value={form.address ?? ''} onChange={(e) => set('address', e.target.value)}
              placeholder="Av. Juárez 123, Centro" />
          </label>
          <div className="tk-grid-2">
            <label className="field">
              <span>Teléfono</span>
              <input value={form.phone ?? ''} onChange={(e) => set('phone', e.target.value)} placeholder="33 1234 5678" />
            </label>
            <label className="field">
              <span>RFC</span>
              <input value={form.rfc ?? ''} onChange={(e) => set('rfc', e.target.value)} placeholder="XAXX010101000" />
            </label>
          </div>
          <label className="field">
            <span>Mensaje de encabezado</span>
            <input value={form.headerMessage ?? ''} onChange={(e) => set('headerMessage', e.target.value)}
              placeholder="El mejor pan de la ciudad" />
          </label>
          <label className="field">
            <span>Mensaje de pie</span>
            <input value={form.footerMessage ?? ''} onChange={(e) => set('footerMessage', e.target.value)}
              placeholder="¡Gracias por su compra!" />
          </label>

          <h3 style={{ marginTop: 'var(--space-4)' }}>Qué mostrar</h3>
          <div className="tk-toggles">
            <Toggle label="Logo" v={form.showLogo} on={(x) => set('showLogo', x)} />
            <Toggle label="Dirección" v={form.showAddress} on={(x) => set('showAddress', x)} />
            <Toggle label="Teléfono" v={form.showPhone} on={(x) => set('showPhone', x)} />
            <Toggle label="RFC" v={form.showRfc} on={(x) => set('showRfc', x)} />
            <Toggle label="Cajero" v={form.showCashier} on={(x) => set('showCashier', x)} />
            <Toggle label="Folio" v={form.showFolio} on={(x) => set('showFolio', x)} />
          </div>

          <h3 style={{ marginTop: 'var(--space-4)' }}>Impresora de tickets</h3>
          <label className="field" style={{ marginTop: 'var(--space-2)' }}>
            <span>Tamaño del rollo de papel</span>
            <select value={form.paperWidthMm}
              onChange={(e) => { const w = Number(e.target.value); set('paperWidthMm', w); setPaperWidth(w); }}>
              <option value={80}>80 mm (estándar)</option>
              <option value={58}>58 mm (compacto)</option>
            </select>
          </label>
          <p className="admin-plan-hint">
            Elige el ancho de tu impresora térmica. La impresión sale a ese tamaño exacto, sin depender
            de la configuración de Windows. Imprime una prueba para verificar que sale completo.
          </p>

          <div style={{ display: 'flex', gap: 10, marginTop: 'var(--space-4)', flexWrap: 'wrap' }}>
            <motion.button className="btn-accent" disabled={save.isPending} onClick={() => save.mutate()}
              whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
              <Save size={16} /> {save.isPending ? 'Guardando…' : 'Guardar configuración'}
            </motion.button>
            <button className="btn-ghost" onClick={() => { setPaperWidth(form.paperWidthMm); printTicketHtml(saleTicketHtml(form, SAMPLE)); }}>
              <Printer size={16} /> Imprimir prueba
            </button>
          </div>
        </motion.div>

        {/* Vista previa en vivo */}
        <motion.div className="card tk-preview-card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3>Vista previa</h3>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Así se verá tu ticket impreso</p>
          <div className="tk-preview">
            <Ticket settings={form} data={SAMPLE} />
          </div>
        </motion.div>
      </div>
    </div>
  );
}

function Toggle({ label, v, on }: { label: string; v: boolean; on: (x: boolean) => void }) {
  return (
    <label className="tk-toggle">
      <input type="checkbox" checked={v} onChange={(e) => on(e.target.checked)} />
      <span>{label}</span>
    </label>
  );
}
