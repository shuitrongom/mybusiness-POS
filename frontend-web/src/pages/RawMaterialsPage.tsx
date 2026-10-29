import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Wheat, PlusCircle, PackagePlus } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { avatarColor, avatarInitials } from '@/lib/saleVisuals';
import { InfoBanner } from '@/components/InfoBanner';
import { fadeInUp } from '@/lib/motion';
import { storedBranchId } from '@/lib/cashierBranch';
import '@/pages/dashboard.css';
import '@/pages/baker.css';
import '@/pages/admin.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const num = (n: number) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 3 }).format(n || 0);

interface Branch { id: number; name: string; active: boolean; }
interface RawMaterialRow {
  rawMaterialId: number;
  name: string;
  sku: string | null;
  unit: string;
  unitCost: number;
  minQuantity: number;
  active: boolean;
  quantity: number;
}
interface ApiProblem { detail?: string; }

const UNITS = ['kg', 'litro', 'pieza'];

/** Error legible del backend (422 regla de negocio). */
function problemMessage(e: unknown, fallback: string): string {
  const data = (e as { response?: { data?: ApiProblem } }).response?.data;
  return data?.detail ?? fallback;
}

/**
 * Gestión de materia prima (admin): alta/edición de insumos (nombre, unidad, costo con
 * prefijo $), reposición de existencia y existencia por sucursal. Aislada del pan y del POS.
 */
export function RawMaterialsPage() {
  const queryClient = useQueryClient();
  const branchId = storedBranchId() || 1;
  const branches = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await api.get<Branch[]>('/branches')).data,
  });
  const resolvedBranch = useMemo(() => {
    const active = (branches.data ?? []).filter((b) => b.active);
    if (active.some((b) => b.id === branchId)) return branchId;
    return active[0]?.id ?? branchId;
  }, [branches.data, branchId]);

  const materials = useQuery({
    queryKey: ['raw-materials', resolvedBranch],
    queryFn: async () => (await api.get<RawMaterialRow[]>('/raw-materials', {
      params: { branchId: resolvedBranch },
    })).data,
  });

  const [form, setForm] = useState({ name: '', unit: 'kg', unitCost: '', minQuantity: '' });

  const createMaterial = useMutation({
    mutationFn: async () => api.post('/raw-materials', {
      name: form.name.trim(),
      unit: form.unit,
      unitCost: form.unitCost ? Number(form.unitCost) : 0,
      minQuantity: form.minQuantity ? Number(form.minQuantity) : 0,
    }),
    onSuccess: () => {
      setForm({ name: '', unit: 'kg', unitCost: '', minQuantity: '' });
      queryClient.invalidateQueries({ queryKey: ['raw-materials'] });
      toast.success('Insumo creado', 'Ya puedes surtir su existencia.');
    },
    onError: (e) => toast.error('No se pudo crear el insumo', problemMessage(e, 'Revisa los datos.')),
  });

  // Reposición de existencia (entrada) por insumo.
  const [entry, setEntry] = useState<{ id: number; name: string } | null>(null);
  const [entryQty, setEntryQty] = useState('');
  const [entryCost, setEntryCost] = useState('');

  const registerEntry = useMutation({
    mutationFn: async () => api.post(`/raw-materials/${entry!.id}/entries`, {
      branchId: resolvedBranch,
      quantity: Number(entryQty),
      unitCost: entryCost ? Number(entryCost) : undefined,
    }),
    onSuccess: () => {
      setEntry(null);
      setEntryQty('');
      setEntryCost('');
      queryClient.invalidateQueries({ queryKey: ['raw-materials'] });
      toast.success('Existencia actualizada', 'La entrada de insumo quedó registrada.');
    },
    onError: (e) => toast.error('No se pudo registrar la entrada', problemMessage(e, 'Intenta de nuevo.')),
  });

  return (
    <div>
      <h1 className="page-title">Materia prima</h1>
      <InfoBanner icon={<Wheat size={18} />} tone="sky" title="Insumos de panadería">
        Da de alta los insumos (harina, azúcar…) con su costo y surte su existencia. La materia prima
        es un inventario aparte: nunca aparece en el punto de venta ni se mezcla con el pan.
      </InfoBanner>

      <motion.div className="card" style={{ marginTop: 'var(--space-3)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><PlusCircle size={18} /> Agregar insumo</h3>
        <div className="admin-grid">
          <label className="field"><span>Nombre</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Harina" /></label>
          <label className="field"><span>Unidad</span>
            <select value={form.unit} onChange={(e) => setForm({ ...form, unit: e.target.value })}>
              {UNITS.map((u) => <option key={u} value={u}>{u}</option>)}
            </select></label>
          <label className="field"><span>Costo unitario</span>
            <div className="bk-money">
              <span className="bk-money-sign">$</span>
              <input type="number" min={0} step="0.0001" placeholder="0.00" value={form.unitCost}
                onChange={(e) => setForm({ ...form, unitCost: e.target.value })} />
            </div></label>
          <label className="field"><span>Alerta de existencia mínima</span>
            <input type="number" min={0} step="0.001" placeholder="0" value={form.minQuantity}
              onChange={(e) => setForm({ ...form, minQuantity: e.target.value })} /></label>
        </div>
        {(!form.unitCost || Number(form.unitCost) === 0) && (
          <p className="admin-sub" style={{ marginTop: 'var(--space-2)' }}>
            Captura el costo para que el margen de las tandas sea confiable.
          </p>
        )}
        <div style={{ marginTop: 'var(--space-4)' }}>
          <button className="btn-accent" disabled={!form.name.trim() || createMaterial.isPending}
            onClick={() => createMaterial.mutate()}>
            {createMaterial.isPending ? 'Creando…' : 'Agregar insumo'}
          </button>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-5)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Wheat size={18} /> Insumos ({materials.data?.length ?? 0})</h3>
        <div className="bk-list">
          <div className="bk-row-head" style={{ gridTemplateColumns: 'minmax(0,1fr) 120px 140px 120px' }}>
            <span>Insumo</span>
            <span className="bk-h-right">Costo</span>
            <span className="bk-h-right">Existencia</span>
            <span className="bk-h-right">Acción</span>
          </div>
          {(materials.data ?? []).map((m) => {
            const low = m.minQuantity > 0 && m.quantity <= m.minQuantity;
            const stockChip = m.quantity <= 0 ? 'is-gray' : low ? 'is-amber' : 'is-green';
            return (
              <div className={`bk-row ${!m.active ? 'is-disabled' : ''}`} key={m.rawMaterialId}
                style={{ gridTemplateColumns: 'minmax(0,1fr) 120px 140px 120px' }}>
                <div className="bk-article">
                  <span className="bk-avatar" style={{ background: avatarColor(m.name) }}>{avatarInitials(m.name)}</span>
                  <div className="bk-article-text">
                    <div className="bk-article-name">{m.name}</div>
                    <div className="bk-article-unit">{m.unit}{m.unitCost <= 0 && ' · sin costo capturado'}</div>
                  </div>
                </div>
                <div style={{ textAlign: 'right' }} className="bk-num">{money(m.unitCost)}</div>
                <div style={{ textAlign: 'right' }}>
                  <span className={`bk-chip ${stockChip}`}>{num(m.quantity)} {m.unit}</span>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <button className="btn-ghost" onClick={() => { setEntry({ id: m.rawMaterialId, name: m.name }); setEntryCost(String(m.unitCost || '')); }}>
                    <PackagePlus size={15} /> Surtir
                  </button>
                </div>
              </div>
            );
          })}
          {!materials.isLoading && (materials.data ?? []).length === 0 && (
            <div className="bk-empty">
              <Wheat size={28} strokeWidth={1.5} />
              <strong>Aún no hay insumos</strong>
              <span>Agrega tu primer insumo arriba (p. ej. harina, azúcar) para empezar.</span>
            </div>
          )}
        </div>
      </motion.div>

      {entry && (
        <div className="bk-modal-backdrop" onClick={() => setEntry(null)}>
          <motion.div className="bk-modal" variants={fadeInUp} initial="hidden" animate="visible"
            onClick={(e) => e.stopPropagation()} style={{ textAlign: 'left' }}>
            <h3 style={{ textAlign: 'center' }}>Surtir {entry.name}</h3>
            <label className="field"><span>Cantidad</span>
              <input type="number" min={0} step="0.001" placeholder="0" value={entryQty}
                onChange={(e) => setEntryQty(e.target.value)} /></label>
            <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Costo unitario (opcional)</span>
              <div className="bk-money">
                <span className="bk-money-sign">$</span>
                <input type="number" min={0} step="0.0001" placeholder="0.00" value={entryCost}
                  onChange={(e) => setEntryCost(e.target.value)} />
              </div></label>
            <div className="bk-actions">
              <button className="bk-btn" onClick={() => setEntry(null)} disabled={registerEntry.isPending}>Cancelar</button>
              <button className="bk-btn bk-btn-primary"
                disabled={registerEntry.isPending || !entryQty || Number(entryQty) <= 0}
                onClick={() => registerEntry.mutate()}>
                {registerEntry.isPending ? 'Guardando…' : 'Registrar entrada'}
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </div>
  );
}
