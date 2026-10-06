import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { PackageSearch, Search, Save } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { SatKeyPicker } from '@/components/SatKeyPicker';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface Prod { id: number; name: string; satProdServ: string | null; satUnit: string | null; taxObject: string | null; retIva: number | null; retIsr: number | null; }

/** Catálogo SAT por producto: asignar clave c_ClaveProdServ, unidad, objeto de impuesto y retenciones. */
export function InvoicingSatProductsPage() {
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [sel, setSel] = useState<Prod | null>(null);
  const [prodServ, setProdServ] = useState('');
  const [unit, setUnit] = useState('H87');
  const [taxObject, setTaxObject] = useState('02');
  const [retIva, setRetIva] = useState('0');
  const [retIsr, setRetIsr] = useState('0');

  const products = useQuery({ queryKey: ['invoicing', 'sat', 'products', q], queryFn: async () => (await api.get<Prod[]>('/invoicing/sat/products', { params: { q } })).data });

  const pick = (p: Prod) => {
    setSel(p);
    setProdServ(p.satProdServ ?? '');
    setUnit(p.satUnit ?? 'H87');
    setTaxObject(p.taxObject ?? '02');
    setRetIva(String(p.retIva ?? 0));
    setRetIsr(String(p.retIsr ?? 0));
  };

  const save = async () => {
    if (!sel) return;
    try {
      await api.post(`/invoicing/sat/products/${sel.id}`, { satProdServ: prodServ, satUnit: unit, taxObject, retIva: Number(retIva), retIsr: Number(retIsr) });
      toast.success('Clave asignada', `${sel.name} → ${prodServ}`);
      qc.invalidateQueries({ queryKey: ['invoicing', 'sat', 'products'] });
    } catch {
      toast.error('No se pudo guardar', 'Revisa la clave SAT.');
    }
  };

  const rows = products.data ?? [];

  return (
    <div>
      <h1 className="page-title">Catálogo SAT: productos</h1>
      <p className="page-sub">Asigna la clave c_ClaveProdServ y la unidad SAT a cada artículo</p>

      <div className="inv-grid2" style={{ alignItems: 'start' }}>
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <div className="cust-search" style={{ marginBottom: 'var(--space-3)' }}><Search size={16} /><input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Buscar artículo…" /></div>
          <div className="inv-table-wrap">
            <table className="inv-table">
              <thead><tr><th>Artículo</th><th>Clave SAT</th><th>Unidad</th></tr></thead>
              <tbody>
                {rows.map((p) => (
                  <tr key={p.id} className={sel?.id === p.id ? 'inv-selrow' : ''} style={{ cursor: 'pointer' }} onClick={() => pick(p)}>
                    <td>{p.name}</td>
                    <td>{p.satProdServ ?? <span style={{ color: 'var(--danger)' }}>sin clave</span>}</td>
                    <td>{p.satUnit ?? '—'}</td>
                  </tr>
                ))}
                {rows.length === 0 && <tr><td colSpan={3}><div className="inv-empty"><PackageSearch size={26} /><p>Sin artículos.</p></div></td></tr>}
              </tbody>
            </table>
          </div>
        </motion.div>

        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title">Asignar clave</h3>
          {!sel ? <div className="inv-empty">Selecciona un artículo.</div> : (
            <>
              <p className="inv-chip" style={{ marginBottom: 'var(--space-3)' }}>{sel.name}</p>
              <label className="field"><span>Clave SAT prod/serv</span>
                <SatKeyPicker kind="prod-serv" value={prodServ} onSelect={(c) => setProdServ(c)} /></label>
              <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Unidad SAT</span>
                <SatKeyPicker kind="unit" value={unit} onSelect={(c) => setUnit(c)} /></label>
              <div className="inv-grid3" style={{ marginTop: 'var(--space-3)' }}>
                <label className="field"><span>Objeto de impuesto</span>
                  <select value={taxObject} onChange={(e) => setTaxObject(e.target.value)}>
                    <option value="01">01 — No objeto</option>
                    <option value="02">02 — Sí objeto</option>
                    <option value="03">03 — Sí objeto, no obligado al desglose</option>
                  </select></label>
                <label className="field"><span>% Retención IVA</span><input type="number" step="0.01" value={retIva} onChange={(e) => setRetIva(e.target.value)} /></label>
                <label className="field"><span>% Retención ISR</span><input type="number" step="0.01" value={retIsr} onChange={(e) => setRetIsr(e.target.value)} /></label>
              </div>
              <button className="btn-accent" style={{ marginTop: 'var(--space-4)' }} onClick={save}><Save size={16} /> Guardar clave</button>
            </>
          )}
        </motion.div>
      </div>
    </div>
  );
}
