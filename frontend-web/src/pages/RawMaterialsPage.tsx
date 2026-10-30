import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  Wheat, PlusCircle, PackagePlus, Pencil, Trash2, History, RotateCcw, PackageOpen,
} from 'lucide-react';
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

/**
 * Fecha/hora del movimiento forzada a la zona de México (America/Mexico_City), independiente
 * del equipo del admin. El helper `dateTime` de @/lib/format NO fija timeZone, por eso aquí se
 * formatea inline con la zona MX para cuadrar con el backend (que guarda en UTC con offset).
 */
const mxDateTime = (iso: string | null | undefined): string =>
  iso
    ? new Date(iso).toLocaleString('es-MX', {
      timeZone: 'America/Mexico_City', dateStyle: 'short', timeStyle: 'short',
    })
    : '—';

/** Grid compartido por cabecera y filas del listado de insumos (alineación por construcción). */
const LIST_GRID = 'minmax(0,1fr) 120px 140px minmax(190px,220px)';

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
interface MovementRow {
  movementId: number;
  movementType: 'CONSUME' | 'PURCHASE_RM' | 'ADJUSTMENT_RM' | string;
  quantity: number;
  balanceAfter: number;
  unitCost: number;
  reason: string | null;
  reference: string | null;
  actor: string | null;
  createdAt: string;
}
interface ApiProblem { detail?: string; title?: string; status?: number; }

const UNITS = ['kg', 'litro', 'pieza'];

/** Error legible del backend (422 regla de negocio). */
function problemMessage(e: unknown, fallback: string): string {
  const data = (e as { response?: { data?: ApiProblem } }).response?.data;
  return data?.detail ?? fallback;
}

/** Al precargar para editar, un 0 se muestra como campo vacío (regla de captura del steering). */
const zeroToBlank = (v: number): string => (v > 0 ? String(v) : '');

/** Tipo de movimiento → etiqueta legible, tono de chip y signo de la cantidad. */
function movementMeta(m: MovementRow): { label: string; chip: string; signed: number } {
  if (m.movementType === 'PURCHASE_RM') {
    return { label: 'Surtido / Compra', chip: 'is-green', signed: Math.abs(m.quantity) };
  }
  if (m.movementType === 'CONSUME') {
    const folio = m.reference?.startsWith('BATCH:') ? m.reference.slice('BATCH:'.length) : null;
    return { label: folio ? `Consumo (tanda T-${folio})` : 'Consumo', chip: 'is-violet', signed: -Math.abs(m.quantity) };
  }
  if (m.movementType === 'ADJUSTMENT_RM') {
    return { label: 'Ajuste', chip: 'is-amber', signed: m.quantity };
  }
  return { label: m.movementType, chip: 'is-gray', signed: m.quantity };
}

/**
 * Gestión de materia prima (admin): alta/edición de insumos (nombre, unidad, costo con prefijo
 * $), reposición de existencia, desactivar/reactivar, borrado duro condicionado (solo insumos
 * vírgenes; el backend es la barrera) y kardex por insumo con fechas en zona MX. Aislada del pan
 * y del POS.
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

  // Edición del insumo (LIBRE, solo admin).
  const [edit, setEdit] = useState<RawMaterialRow | null>(null);
  const [editForm, setEditForm] = useState({ name: '', unit: 'kg', unitCost: '', minQuantity: '' });

  const openEdit = (m: RawMaterialRow) => {
    setEdit(m);
    setEditForm({
      name: m.name,
      unit: m.unit,
      unitCost: zeroToBlank(m.unitCost),
      minQuantity: zeroToBlank(m.minQuantity),
    });
  };

  const updateMaterial = useMutation({
    mutationFn: async () => api.put(`/raw-materials/${edit!.rawMaterialId}`, {
      sku: edit!.sku, // se conserva el SKU actual (no se captura en el form, paridad con el alta)
      name: editForm.name.trim(),
      unit: editForm.unit,
      unitCost: editForm.unitCost ? Number(editForm.unitCost) : 0,
      minQuantity: editForm.minQuantity ? Number(editForm.minQuantity) : 0,
    }),
    onSuccess: () => {
      setEdit(null);
      queryClient.invalidateQueries({ queryKey: ['raw-materials'] });
      toast.success('Insumo actualizado', 'Los cambios se guardaron.');
    },
    onError: (e) => toast.error('No se pudo guardar', problemMessage(e, 'Revisa los datos.')),
  });

  // Desactivar / reactivar.
  const setActive = useMutation({
    mutationFn: async (v: { id: number; active: boolean }) =>
      api.post(`/raw-materials/${v.id}/active`, { active: v.active }),
    onSuccess: (_d, v) => {
      setRemove(null);
      queryClient.invalidateQueries({ queryKey: ['raw-materials'] });
      toast.success(v.active ? 'Insumo reactivado' : 'Insumo desactivado',
        v.active ? 'Vuelve a estar disponible para las tandas.' : 'Ya no aparece para el panadero; su historial se conserva.');
    },
    onError: (e) => toast.error('No se pudo cambiar el estado', problemMessage(e, 'Intenta de nuevo.')),
  });

  // Borrado duro condicionado. La UI es conveniencia; el backend es la barrera (409 si no es virgen).
  const [remove, setRemove] = useState<RawMaterialRow | null>(null);

  const deleteMaterial = useMutation({
    mutationFn: async (id: number) => api.delete(`/raw-materials/${id}`),
    onSuccess: () => {
      setRemove(null);
      queryClient.invalidateQueries({ queryKey: ['raw-materials'] });
      toast.success('Insumo borrado', 'Se eliminó porque nunca tuvo existencia ni movimientos.');
    },
    onError: (e, id) => {
      const status = (e as { response?: { status?: number } }).response?.status;
      if (status === 409) {
        // Tiene movimientos históricos aunque el stock esté en 0: se degrada a desactivar.
        toast.info('Tiene movimientos', 'No se puede borrar; se desactivará para conservar el historial.');
        setActive.mutate({ id, active: false });
      } else {
        toast.error('No se pudo borrar', problemMessage(e, 'Intenta de nuevo.'));
      }
    },
  });

  // Kardex / historial por insumo.
  const [kardex, setKardex] = useState<{ id: number; name: string; unit: string } | null>(null);
  const movements = useQuery({
    queryKey: ['rm-movements', kardex?.id],
    enabled: !!kardex,
    queryFn: async () => (await api.get<MovementRow[]>(`/raw-materials/${kardex!.id}/movements`)).data,
  });

  const kardexTotals = useMemo(() => {
    const list = movements.data ?? [];
    let inQty = 0;
    let outQty = 0;
    for (const m of list) {
      const q = Math.abs(m.quantity);
      if (m.movementType === 'CONSUME') outQty += q;
      else inQty += q;
    }
    return { inQty, outQty };
  }, [movements.data]);

  return (
    <div>
      <h1 className="page-title">Materia prima</h1>
      <InfoBanner icon={<Wheat size={18} />} tone="sky" title="Insumos de panadería">
        Da de alta los insumos (harina, azúcar…) con su costo, surte su existencia, edítalos y
        consulta su historial. Puedes desactivar un insumo para conservar su kardex o borrarlo si
        nunca se usó. La materia prima es un inventario aparte: nunca aparece en el punto de venta.
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
          <div className="bk-row-head" style={{ gridTemplateColumns: LIST_GRID }}>
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
                style={{ gridTemplateColumns: LIST_GRID }}>
                <div className="bk-article">
                  <span className="bk-avatar" style={{ background: avatarColor(m.name) }}>{avatarInitials(m.name)}</span>
                  <div className="bk-article-text">
                    <div className="bk-article-name">{m.name}</div>
                    <div className="bk-article-unit">
                      {!m.active && <span className="bk-chip is-gray">Inactivo</span>}
                      {m.active && <>{m.unit}{m.unitCost <= 0 && ' · sin costo capturado'}</>}
                    </div>
                  </div>
                </div>
                <div style={{ textAlign: 'right' }} className="bk-num">{money(m.unitCost)}</div>
                <div style={{ textAlign: 'right' }}>
                  <span className={`bk-chip ${stockChip}`}>{num(m.quantity)} {m.unit}</span>
                </div>
                <div className="bk-row-actions">
                  {m.active ? (
                    <>
                      <button className="btn-ghost" title="Editar" onClick={() => openEdit(m)}>
                        <Pencil size={15} /> Editar
                      </button>
                      <button className="btn-ghost" title="Surtir existencia"
                        onClick={() => { setEntry({ id: m.rawMaterialId, name: m.name }); setEntryCost(String(m.unitCost || '')); }}>
                        <PackagePlus size={15} /> Surtir
                      </button>
                      <button className="btn-ghost" title="Ver movimientos"
                        onClick={() => setKardex({ id: m.rawMaterialId, name: m.name, unit: m.unit })}>
                        <History size={15} />
                      </button>
                      <button className="btn-ghost" title="Eliminar" onClick={() => setRemove(m)}>
                        <Trash2 size={15} />
                      </button>
                    </>
                  ) : (
                    <>
                      <button className="btn-ghost" title="Ver movimientos"
                        onClick={() => setKardex({ id: m.rawMaterialId, name: m.name, unit: m.unit })}>
                        <History size={15} />
                      </button>
                      <button className="btn-ghost" title="Reactivar" disabled={setActive.isPending}
                        onClick={() => setActive.mutate({ id: m.rawMaterialId, active: true })}>
                        <RotateCcw size={15} /> Reactivar
                      </button>
                    </>
                  )}
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

      {/* Modal: surtir existencia */}
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

      {/* Modal: editar insumo */}
      {edit && (
        <div className="bk-modal-backdrop" onClick={() => setEdit(null)}>
          <motion.div className="bk-modal" variants={fadeInUp} initial="hidden" animate="visible"
            onClick={(e) => e.stopPropagation()} style={{ textAlign: 'left' }}>
            <h3 style={{ textAlign: 'center' }}>Editar insumo</h3>
            <label className="field"><span>Nombre</span>
              <input value={editForm.name} placeholder="Harina"
                onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} /></label>
            <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Unidad</span>
              <select value={editForm.unit} onChange={(e) => setEditForm({ ...editForm, unit: e.target.value })}>
                {UNITS.map((u) => <option key={u} value={u}>{u}</option>)}
              </select></label>
            <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Costo unitario</span>
              <div className="bk-money">
                <span className="bk-money-sign">$</span>
                <input type="number" min={0} step="0.0001" placeholder="0.00" value={editForm.unitCost}
                  onChange={(e) => setEditForm({ ...editForm, unitCost: e.target.value })} />
              </div></label>
            <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Alerta de existencia mínima</span>
              <input type="number" min={0} step="0.001" placeholder="0" value={editForm.minQuantity}
                onChange={(e) => setEditForm({ ...editForm, minQuantity: e.target.value })} /></label>
            {(!editForm.unitCost || Number(editForm.unitCost) === 0) && (
              <p className="admin-sub" style={{ marginTop: 'var(--space-2)' }}>
                Captura el costo para que el margen de las tandas sea confiable.
              </p>
            )}
            <div className="bk-actions">
              <button className="bk-btn" onClick={() => setEdit(null)} disabled={updateMaterial.isPending}>Cancelar</button>
              <button className="bk-btn bk-btn-primary"
                disabled={updateMaterial.isPending || !editForm.name.trim()}
                onClick={() => updateMaterial.mutate()}>
                {updateMaterial.isPending ? 'Guardando…' : 'Guardar cambios'}
              </button>
            </div>
          </motion.div>
        </div>
      )}

      {/* Modal: eliminar / desactivar */}
      {remove && (
        <div className="bk-modal-backdrop" onClick={() => setRemove(null)}>
          <motion.div className="bk-modal" variants={fadeInUp} initial="hidden" animate="visible"
            onClick={(e) => e.stopPropagation()} style={{ textAlign: 'left' }}>
            <div className="bk-modal-icon is-err" style={{ margin: '0 auto var(--space-3)' }}><Trash2 size={28} /></div>
            <h3 style={{ textAlign: 'center' }}>Eliminar {remove.name}</h3>
            <p style={{ color: 'var(--text-muted)' }}>
              Si el insumo nunca tuvo existencia ni movimientos se borra por completo. Si ya se usó,
              solo se desactiva para conservar su historial de costos.
            </p>
            {remove.quantity > 0 ? (
              <div className="bk-actions" style={{ flexDirection: 'column' }}>
                <button className="bk-btn bk-btn-primary" disabled={setActive.isPending}
                  onClick={() => setActive.mutate({ id: remove.rawMaterialId, active: false })}>
                  {setActive.isPending ? 'Desactivando…' : 'Desactivar (tiene existencia)'}
                </button>
                <button className="bk-btn" onClick={() => setRemove(null)}>Cancelar</button>
              </div>
            ) : (
              <div className="bk-actions" style={{ flexDirection: 'column' }}>
                <button className="bk-btn bk-btn-primary" disabled={deleteMaterial.isPending}
                  onClick={() => deleteMaterial.mutate(remove.rawMaterialId)}>
                  {deleteMaterial.isPending ? 'Borrando…' : 'Borrar definitivamente'}
                </button>
                <button className="bk-btn" disabled={setActive.isPending}
                  onClick={() => setActive.mutate({ id: remove.rawMaterialId, active: false })}>
                  Mejor desactivar
                </button>
                <button className="bk-btn" onClick={() => setRemove(null)}>Cancelar</button>
              </div>
            )}
          </motion.div>
        </div>
      )}

      {/* Modal: kardex / historial de movimientos */}
      {kardex && (
        <div className="bk-modal-backdrop" onClick={() => setKardex(null)}>
          <motion.div className="bk-modal bk-modal-wide" variants={fadeInUp} initial="hidden" animate="visible"
            onClick={(e) => e.stopPropagation()} style={{ textAlign: 'left' }}>
            <h3 style={{ textAlign: 'center' }}>Movimientos de {kardex.name}</h3>
            <div className="bk-list">
              <div className="bk-row-head bk-kardex-row">
                <span>Fecha</span>
                <span>Tipo</span>
                <span className="bk-h-right">Cantidad</span>
                <span className="bk-h-right">Balance</span>
                <span className="bk-h-right">Costo</span>
              </div>
              {(movements.data ?? []).map((mv) => {
                const meta = movementMeta(mv);
                return (
                  <div className="bk-row bk-kardex-row" key={mv.movementId}>
                    <span className="bk-num">{mxDateTime(mv.createdAt)}</span>
                    <span>
                      <span className={`bk-chip ${meta.chip}`}>{meta.label}</span>
                      {mv.actor && <div className="bk-article-unit" style={{ marginTop: 4 }}>{mv.actor}</div>}
                    </span>
                    <span className="bk-num" style={{ textAlign: 'right', fontWeight: 700 }}>
                      {meta.signed > 0 ? '+' : meta.signed < 0 ? '−' : ''}{num(Math.abs(mv.quantity))} {kardex.unit}
                    </span>
                    <span className="bk-num" style={{ textAlign: 'right', color: 'var(--text-muted)' }}>
                      {num(mv.balanceAfter)}
                    </span>
                    <span className="bk-num" style={{ textAlign: 'right' }}>{money(mv.unitCost)}</span>
                  </div>
                );
              })}
              {!movements.isLoading && (movements.data ?? []).length === 0 && (
                <div className="bk-empty">
                  <PackageOpen size={28} strokeWidth={1.5} />
                  <strong>Aún no hay movimientos</strong>
                  <span>Surte existencia con el botón Surtir para registrar la primera entrada.</span>
                </div>
              )}
            </div>
            {(movements.data ?? []).length > 0 && (
              <div className="bk-foot">
                <div className="bk-foot-row">
                  <span>Total entradas</span>
                  <span className="bk-num">{num(kardexTotals.inQty)} {kardex.unit}</span>
                </div>
                <div className="bk-foot-row">
                  <span>Total consumido</span>
                  <span className="bk-num">{num(kardexTotals.outQty)} {kardex.unit}</span>
                </div>
              </div>
            )}
            <div className="bk-actions">
              <button className="bk-btn bk-btn-primary" onClick={() => setKardex(null)}>Cerrar</button>
            </div>
          </motion.div>
        </div>
      )}
    </div>
  );
}
