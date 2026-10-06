import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Layers, Save } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { SatKeyPicker } from '@/components/SatKeyPicker';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Line { lineName: string; satProdServ: string | null; satUnit: string | null; taxObject: string | null; }

/** Catálogo SAT por línea (categoría): asigna clave en bloque a todos los productos de la línea. */
export function InvoicingSatLinesPage() {
  const qc = useQueryClient();
  const [line, setLine] = useState('');
  const [prodServ, setProdServ] = useState('');
  const [unit, setUnit] = useState('H87');
  const [taxObject, setTaxObject] = useState('02');

  const lines = useQuery({ queryKey: ['invoicing', 'sat', 'lines'], queryFn: async () => (await api.get<Line[]>('/invoicing/sat/lines')).data });
  const rows = lines.data ?? [];

  const save = async () => {
    if (!line || !prodServ) { toast.error('Faltan datos', 'Elige la línea y la clave SAT.'); return; }
    try {
      const { data } = await api.post('/invoicing/sat/lines', { lineName: line, satProdServ: prodServ, satUnit: unit, taxObject });
      toast.success('Clave asignada a la línea', `${data.productsUpdated} artículos actualizados.`);
      qc.invalidateQueries({ queryKey: ['invoicing', 'sat', 'lines'] });
    } catch {
      toast.error('No se pudo guardar', 'Revisa los datos.');
    }
  };

  return (
    <div>
      <h1 className="page-title">Catálogo SAT: líneas</h1>
      <p className="page-sub">Asigna la clave SAT por línea (categoría) y se propaga a todos sus artículos</p>

      <div className="inv-grid2" style={{ alignItems: 'start' }}>
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Layers size={18} /> Asignar por línea</h3>
          <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Línea (categoría)</span>
            <select value={line} onChange={(e) => setLine(e.target.value)}>
              <option value="">Selecciona…</option>
              {rows.map((l) => <option key={l.lineName} value={l.lineName}>{l.lineName}</option>)}
            </select></label>
          <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Clave SAT prod/serv</span>
            <SatKeyPicker kind="prod-serv" value={prodServ} onSelect={(c) => setProdServ(c)} /></label>
          <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Unidad SAT</span>
            <SatKeyPicker kind="unit" value={unit} onSelect={(c) => setUnit(c)} /></label>
          <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Objeto de impuesto</span>
            <select value={taxObject} onChange={(e) => setTaxObject(e.target.value)}>
              <option value="01">01 — No objeto</option>
              <option value="02">02 — Sí objeto</option>
              <option value="03">03 — Sí objeto, no obligado al desglose</option>
            </select></label>
          <button className="btn-accent" style={{ marginTop: 'var(--space-4)' }} onClick={save}><Save size={16} /> Asignar a la línea</button>
        </motion.div>

        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title">Líneas y su clave SAT</h3>
          <div className="inv-table-wrap" style={{ marginTop: 'var(--space-2)' }}>
            <table className="inv-table">
              <thead><tr><th>Línea</th><th>Clave SAT</th><th>Unidad</th></tr></thead>
              <tbody>
                {rows.map((l) => (
                  <tr key={l.lineName}>
                    <td>{l.lineName}</td>
                    <td>{l.satProdServ ?? <span style={{ color: 'var(--danger)' }}>sin clave</span>}</td>
                    <td>{l.satUnit ?? '—'}</td>
                  </tr>
                ))}
                {rows.length === 0 && <tr><td colSpan={3}><div className="inv-empty">Sin líneas/categorías.</div></td></tr>}
              </tbody>
            </table>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
