import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Truck, Plus, Trash2 } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

type Tab = 'permiso' | 'vehiculo' | 'remolque' | 'operador' | 'ubicacion';
const TABS: { id: Tab; label: string }[] = [
  { id: 'permiso', label: 'Permiso' },
  { id: 'vehiculo', label: 'Vehículo' },
  { id: 'remolque', label: 'Remolque' },
  { id: 'operador', label: 'Operador' },
  { id: 'ubicacion', label: 'Ubicaciones' },
];

/** Catálogos del complemento Carta Porte 3.1 (autotransporte). */
export function InvoicingCartaPortePage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>('permiso');
  const [form, setForm] = useState<Record<string, string>>({});

  const list = useQuery({
    queryKey: ['invoicing', 'carta-porte', tab],
    queryFn: async () => (await api.get<Record<string, unknown>[]>(`/invoicing/carta-porte/${tab}`)).data,
  });

  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const add = async () => {
    try {
      await api.post(`/invoicing/carta-porte/${tab}`, buildBody(tab, form));
      toast.success('Registro agregado', 'El catálogo de carta porte se actualizó.');
      setForm({});
      qc.invalidateQueries({ queryKey: ['invoicing', 'carta-porte', tab] });
    } catch {
      toast.error('No se pudo agregar', 'Revisa los datos.');
    }
  };

  const del = async (id: number) => {
    try {
      await api.delete(`/invoicing/carta-porte/${tab}/${id}`);
      qc.invalidateQueries({ queryKey: ['invoicing', 'carta-porte', tab] });
    } catch {
      toast.error('No se pudo eliminar', '');
    }
  };

  const rows = list.data ?? [];

  return (
    <div>
      <h1 className="page-title">Catálogos Carta Porte</h1>
      <p className="page-sub">Datos maestros del complemento Carta Porte 3.1 (autotransporte de carga)</p>

      <div className="inv-cat-tabs">
        {TABS.map((t) => (
          <button key={t.id} className={`inv-cat-tab ${tab === t.id ? 'is-on' : ''}`} onClick={() => { setTab(t.id); setForm({}); }}>{t.label}</button>
        ))}
      </div>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible" key={tab}>
        <h3 className="sec-title"><Truck size={18} /> Agregar {TABS.find((t) => t.id === tab)?.label}</h3>
        <div className="inv-grid3" style={{ marginTop: 'var(--space-3)' }}>
          {fieldsFor(tab).map((f) => (
            <label key={f.key} className="field"><span>{f.label}</span>
              <input value={form[f.key] ?? ''} onChange={(e) => set(f.key, e.target.value)} placeholder={f.ph ?? ''} /></label>
          ))}
        </div>
        <button className="btn-accent" style={{ marginTop: 'var(--space-3)' }} onClick={add}><Plus size={16} /> Agregar</button>

        <div className="inv-table-wrap" style={{ marginTop: 'var(--space-4)' }}>
          <table className="inv-table">
            <thead><tr>{columnsFor(tab).map((c) => <th key={c}>{c}</th>)}<th></th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={String(r.id)}>
                  {valuesFor(tab, r).map((v, i) => <td key={i}>{v}</td>)}
                  <td><button className="btn-ghost" onClick={() => del(Number(r.id))}><Trash2 size={14} /></button></td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={columnsFor(tab).length + 1}><div className="inv-empty">Sin registros en este catálogo.</div></td></tr>}
            </tbody>
          </table>
        </div>
      </motion.div>
    </div>
  );
}

function fieldsFor(tab: Tab): { key: string; label: string; ph?: string }[] {
  switch (tab) {
    case 'permiso': return [{ key: 'tipoPermiso', label: 'Tipo de permiso', ph: 'TPAF01' }, { key: 'numero', label: 'Número SICT' }, { key: 'descripcion', label: 'Descripción' }];
    case 'vehiculo': return [{ key: 'configVehic', label: 'Config. vehicular', ph: 'C2, C3, T3S2' }, { key: 'placa', label: 'Placa' }, { key: 'anioModelo', label: 'Año modelo' }, { key: 'aseguradora', label: 'Aseguradora' }, { key: 'polizaSeguro', label: 'Póliza de seguro' }];
    case 'remolque': return [{ key: 'subtipo', label: 'Subtipo remolque', ph: 'CTR001' }, { key: 'placa', label: 'Placa' }];
    case 'operador': return [{ key: 'nombre', label: 'Nombre' }, { key: 'rfc', label: 'RFC' }, { key: 'curp', label: 'CURP' }, { key: 'numLicencia', label: 'No. de licencia' }];
    case 'ubicacion': return [{ key: 'tipo', label: 'Tipo (Origen/Destino)' }, { key: 'nombre', label: 'Nombre' }, { key: 'rfc', label: 'RFC' }, { key: 'calle', label: 'Calle' }, { key: 'municipio', label: 'Municipio' }, { key: 'estado', label: 'Estado' }, { key: 'cp', label: 'CP' }];
  }
}

function buildBody(tab: Tab, form: Record<string, string>): Record<string, unknown> {
  if (tab === 'vehiculo') return { ...form, anioModelo: form.anioModelo ? Number(form.anioModelo) : null };
  return { ...form };
}

function columnsFor(tab: Tab): string[] {
  switch (tab) {
    case 'permiso': return ['Tipo', 'Número', 'Descripción'];
    case 'vehiculo': return ['Config.', 'Placa', 'Año', 'Aseguradora', 'Póliza'];
    case 'remolque': return ['Subtipo', 'Placa'];
    case 'operador': return ['Nombre', 'RFC', 'CURP', 'Licencia'];
    case 'ubicacion': return ['Tipo', 'Nombre', 'Municipio', 'Estado', 'CP'];
  }
}

function valuesFor(tab: Tab, r: Record<string, unknown>): string[] {
  const s = (k: string) => String(r[k] ?? '');
  switch (tab) {
    case 'permiso': return [s('tipoPermiso'), s('numero'), s('descripcion')];
    case 'vehiculo': return [s('configVehic'), s('placa'), s('anioModelo'), s('aseguradora'), s('polizaSeguro')];
    case 'remolque': return [s('subtipo'), s('placa')];
    case 'operador': return [s('nombre'), s('rfc'), s('curp'), s('numLicencia')];
    case 'ubicacion': return [s('tipo'), s('nombre'), s('municipio'), s('estado'), s('cp')];
  }
}
