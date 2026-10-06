import { useState } from 'react';
import { motion } from 'motion/react';
import { Zap, Check } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';

interface Created { id: number; code: string; description: string; }

/**
 * Alta rápida de artículos: crea un producto mínimo (código, descripción, costo, precio, clave y
 * unidad SAT) en un solo paso. Ideal para capturar productos al vuelo.
 */
export function InventoryQuickPage() {
  const [form, setForm] = useState({
    code: '', description: '', cost: '', price: '', satKey: '', satUnit: '', unit: 'pieza', withoutVat: false,
  });
  const [busy, setBusy] = useState(false);
  const [recent, setRecent] = useState<Created[]>([]);

  const set = (k: keyof typeof form, v: string | boolean) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async () => {
    if (!form.description.trim()) { toast.info('Escribe la descripción del producto'); return; }
    setBusy(true);
    try {
      const { data: id } = await api.post<number>('/inventory/quick-product', {
        code: form.code.trim() || null,
        description: form.description.trim(),
        cost: form.cost ? Number(form.cost) : 0,
        price: form.price ? Number(form.price) : 0,
        satKey: form.satKey.trim() || null,
        satUnit: form.satUnit.trim() || null,
        unit: form.unit.trim() || 'pieza',
        withoutVat: form.withoutVat,
      });
      toast.success('Artículo creado', `${form.description} (id ${id}).`);
      setRecent((r) => [{ id, code: form.code, description: form.description }, ...r].slice(0, 10));
      setForm({ ...form, code: '', description: '', cost: '', price: '', satKey: '', satUnit: '' });
    } catch { toast.error('No se pudo crear el artículo'); } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Alta rápida de artículos</h1>
      <p className="page-sub">Crea productos al vuelo con lo esencial; edítalos después en el catálogo</p>

      <motion.div className="card" style={{ maxWidth: 640 }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Zap size={18} /> Nuevo artículo</h3>
        <div className="drawer-section" style={{ marginTop: 'var(--space-3)' }}>
          <div className="grid-2">
            <label className="field"><span>Código / clave del producto</span>
              <input value={form.code} onChange={(e) => set('code', e.target.value)} autoFocus /></label>
            <label className="field"><span>Unidad</span>
              <input value={form.unit} onChange={(e) => set('unit', e.target.value)} /></label>
          </div>
          <label className="field"><span>Descripción *</span>
            <input value={form.description} onChange={(e) => set('description', e.target.value)} /></label>
          <div className="grid-2">
            <label className="field"><span>Precio de costo</span>
              <input type="number" step="0.01" value={form.cost} onChange={(e) => set('cost', e.target.value)} /></label>
            <label className="field"><span>Precio de venta</span>
              <input type="number" step="0.01" value={form.price} onChange={(e) => set('price', e.target.value)} /></label>
          </div>
          <div className="grid-2">
            <label className="field"><span>Clave SAT (ProdServ)</span>
              <input value={form.satKey} onChange={(e) => set('satKey', e.target.value)} placeholder="Ej. 50202306" /></label>
            <label className="field"><span>Unidad SAT</span>
              <input value={form.satUnit} onChange={(e) => set('satUnit', e.target.value)} placeholder="Ej. H87" /></label>
          </div>
          <label className="switch-row">
            <input type="checkbox" checked={form.withoutVat} onChange={(e) => set('withoutVat', e.target.checked)} />
            <span>Artículo sin I.V.A.</span>
          </label>
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button className="btn-accent" disabled={busy} onClick={submit}>
              <Check size={16} /> {busy ? 'Creando…' : 'Crear artículo'}
            </button>
          </div>
        </div>
      </motion.div>

      {recent.length > 0 && (
        <motion.div className="card" style={{ marginTop: 'var(--space-4)', maxWidth: 640 }} variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title">Creados en esta sesión</h3>
          <table className="cust-table">
            <thead><tr><th>Id</th><th>Código</th><th>Descripción</th></tr></thead>
            <tbody>
              {recent.map((r) => (
                <tr key={r.id}><td>{r.id}</td><td>{r.code || '—'}</td><td>{r.description}</td></tr>
              ))}
            </tbody>
          </table>
        </motion.div>
      )}
    </div>
  );
}
