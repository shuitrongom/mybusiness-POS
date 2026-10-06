import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import {
  Truck, UserPlus, Search, X, Info, MapPin, CreditCard, Users as UsersIcon,
  Upload, Pencil, Trash2, Plus, Phone, Mail,
} from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp, pressable } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/products.css';
import '@/pages/pos.css';
import '@/pages/customers.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface SupplierRow {
  id: number; externalCode: string | null; name: string; rfc: string | null;
  phone: string | null; email: string | null; city: string | null; state: string | null;
  creditLimit: number; creditDays: number; classification: string | null;
}
interface Contact { name: string; role: string; phone: string; email: string; }
interface SupplierDetail {
  id: number; externalCode: string | null; name: string; rfc: string | null;
  phone: string | null; email: string | null; country: string | null; zipCode: string | null;
  street: string | null; neighborhood: string | null; town: string | null; city: string | null;
  state: string | null; creditDays: number; creditLimit: number;
  discount1: number; discount2: number; discount3: number; discount4: number; discount5: number;
  classification: string | null; reviewPayment: string | null;
  affectsInventoryOnly: boolean; skipPayable: boolean; notes: string | null;
  visitPeriodicity: string | null; visitDays: string | null;
  contacts: Contact[];
}

interface Draft {
  externalCode: string; name: string; rfc: string; phone: string; email: string;
  country: string; zipCode: string; street: string; neighborhood: string; town: string;
  city: string; state: string; creditDays: string; creditLimit: string;
  discount1: string; discount2: string; discount3: string; discount4: string; discount5: string;
  classification: string; reviewPayment: string; affectsInventoryOnly: boolean; skipPayable: boolean;
  notes: string; visitPeriodicity: string; visitDays: string; contacts: Contact[];
}

function emptyDraft(): Draft {
  return {
    externalCode: '', name: '', rfc: '', phone: '', email: '',
    country: 'México', zipCode: '', street: '', neighborhood: '', town: '',
    city: '', state: '', creditDays: '', creditLimit: '',
    discount1: '', discount2: '', discount3: '', discount4: '', discount5: '',
    classification: 'GENERAL', reviewPayment: '', affectsInventoryOnly: false, skipPayable: false,
    notes: '', visitPeriodicity: 'NONE', visitDays: '', contacts: [],
  };
}
function draftFrom(s: SupplierDetail): Draft {
  return {
    externalCode: s.externalCode ?? '', name: s.name, rfc: s.rfc ?? '', phone: s.phone ?? '',
    email: s.email ?? '', country: s.country ?? 'México', zipCode: s.zipCode ?? '',
    street: s.street ?? '', neighborhood: s.neighborhood ?? '', town: s.town ?? '',
    city: s.city ?? '', state: s.state ?? '', creditDays: String(s.creditDays ?? 0),
    creditLimit: String(s.creditLimit ?? 0),
    discount1: String(s.discount1 ?? 0), discount2: String(s.discount2 ?? 0),
    discount3: String(s.discount3 ?? 0), discount4: String(s.discount4 ?? 0), discount5: String(s.discount5 ?? 0),
    classification: s.classification ?? 'GENERAL', reviewPayment: s.reviewPayment ?? '',
    affectsInventoryOnly: s.affectsInventoryOnly, skipPayable: s.skipPayable, notes: s.notes ?? '',
    visitPeriodicity: s.visitPeriodicity ?? 'NONE', visitDays: s.visitDays ?? '', contacts: s.contacts ?? [],
  };
}
function toPayload(d: Draft) {
  const num = (s: string) => (s === '' ? null : Number(s));
  return {
    externalCode: d.externalCode.trim() || null, name: d.name.trim(),
    rfc: d.rfc.trim().toUpperCase() || null, phone: d.phone.trim() || null,
    email: d.email.trim() || null, country: d.country.trim() || 'México',
    zipCode: d.zipCode.trim() || null, street: d.street.trim() || null,
    neighborhood: d.neighborhood.trim() || null, town: d.town.trim() || null,
    city: d.city.trim() || null, state: d.state.trim() || null,
    creditDays: num(d.creditDays), creditLimit: num(d.creditLimit),
    discount1: num(d.discount1), discount2: num(d.discount2), discount3: num(d.discount3),
    discount4: num(d.discount4), discount5: num(d.discount5),
    classification: d.classification.trim() || 'GENERAL', reviewPayment: d.reviewPayment.trim() || null,
    affectsInventoryOnly: d.affectsInventoryOnly, skipPayable: d.skipPayable,
    notes: d.notes.trim() || null, visitPeriodicity: d.visitPeriodicity, visitDays: d.visitDays.trim() || null,
    contacts: d.contacts.filter((c) => c.name.trim()),
  };
}

const TILE_COLORS = ['#ea580c', '#2563eb', '#16a34a', '#7c3aed', '#0891b2', '#e11d48'];
function colorFor(t: string): string {
  let h = 0; for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) >>> 0;
  return TILE_COLORS[h % TILE_COLORS.length];
}
function initials(name: string): string {
  const w = name.trim().split(/\s+/).filter(Boolean);
  if (!w.length) return '?';
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase();
}

type Tab = 'general' | 'address' | 'credit' | 'contacts';

/**
 * Proveedores ENTERPRISE: tabla premium con búsqueda + editor slide-over con pestañas (Generales,
 * Dirección, Crédito y descuentos, Contactos) e importación en lote.
 */
export function SuppliersPage() {
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [tab, setTab] = useState<Tab>('general');
  const [draft, setDraft] = useState<Draft>(emptyDraft());
  const [importOpen, setImportOpen] = useState(params.get('import') === '1');

  useEffect(() => {
    if (params.get('import') === '1') {
      setImportOpen(true);
      params.delete('import');
      setParams(params, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const { data: suppliers = [], isLoading } = useQuery({
    queryKey: ['suppliers', search],
    queryFn: async () => (await api.get<SupplierRow[]>('/purchasing/suppliers', { params: { q: search } })).data,
  });

  const save = useMutation({
    mutationFn: async () => {
      const payload = toPayload(draft);
      if (editingId) return api.put(`/purchasing/suppliers/${editingId}`, payload);
      return api.post('/purchasing/suppliers/full', payload);
    },
    onSuccess: () => {
      setEditorOpen(false);
      queryClient.invalidateQueries({ queryKey: ['suppliers'] });
      toast.success(editingId ? 'Proveedor actualizado' : 'Proveedor creado', 'Datos guardados.');
    },
    onError: () => toast.error('No se pudo guardar el proveedor'),
  });
  const remove = useMutation({
    mutationFn: async (id: number) => api.delete(`/purchasing/suppliers/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['suppliers'] });
      toast.success('Proveedor desactivado');
    },
    onError: () => toast.error('No se pudo desactivar'),
  });

  function openCreate() { setEditingId(null); setDraft(emptyDraft()); setTab('general'); setEditorOpen(true); }
  async function openEdit(id: number) {
    try {
      const { data } = await api.get<SupplierDetail>(`/purchasing/suppliers/${id}`);
      setEditingId(id); setDraft(draftFrom(data)); setTab('general'); setEditorOpen(true);
    } catch { toast.error('No se pudo cargar el proveedor'); }
  }
  function set<K extends keyof Draft>(k: K, v: Draft[K]) { setDraft((d) => ({ ...d, [k]: v })); }
  function addContact() { setDraft((d) => ({ ...d, contacts: [...d.contacts, { name: '', role: '', phone: '', email: '' }] })); }
  function setContact(i: number, patch: Partial<Contact>) {
    setDraft((d) => ({ ...d, contacts: d.contacts.map((c, idx) => (idx === i ? { ...c, ...patch } : c)) }));
  }
  function removeContact(i: number) { setDraft((d) => ({ ...d, contacts: d.contacts.filter((_, idx) => idx !== i) })); }

  const rows = useMemo(() => suppliers, [suppliers]);

  return (
    <div>
      <div className="page-head-row">
        <div>
          <h1 className="page-title">Proveedores</h1>
          <p className="page-sub">Catálogo de proveedores, crédito, descuentos y contactos</p>
        </div>
        <div className="page-head-actions">
          <button className="btn-ghost" onClick={() => setImportOpen(true)}><Upload size={16} /> Importar</button>
          <motion.button className="btn-accent" onClick={openCreate}
            whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
            <UserPlus size={16} /> Nuevo proveedor
          </motion.button>
        </div>
      </div>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="cust-toolbar">
          <div className="cust-search">
            <Search size={16} />
            <input value={search} onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por nombre, RFC o clave…" />
          </div>
          <span className="cust-count"><Truck size={15} /> {rows.length} proveedor(es)</span>
        </div>

        <div className="cust-table-wrap">
          <table className="cust-table">
            <thead>
              <tr>
                <th>Proveedor</th><th>RFC</th><th>Contacto</th><th>Ciudad</th>
                <th className="ta-right">Límite crédito</th><th className="ta-right">Días</th>
                <th className="ta-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.id}>
                  <td>
                    <div className="cust-cell">
                      <span className="cust-avatar" style={{ background: colorFor(s.name) }}>{initials(s.name)}</span>
                      <div>
                        <strong>{s.name}</strong>
                        {s.externalCode && <div className="muted" style={{ fontSize: 12 }}>{s.externalCode}</div>}
                      </div>
                    </div>
                  </td>
                  <td>{s.rfc || <span className="muted">—</span>}</td>
                  <td>
                    <div className="cust-contact">
                      {s.phone && <span><Phone size={12} /> {s.phone}</span>}
                      {s.email && <span><Mail size={12} /> {s.email}</span>}
                      {!s.phone && !s.email && <span className="muted">—</span>}
                    </div>
                  </td>
                  <td>{s.city || <span className="muted">—</span>}</td>
                  <td className="ta-right">{money(s.creditLimit)}</td>
                  <td className="ta-right">{s.creditDays}</td>
                  <td className="ta-right">
                    <div className="cust-actions">
                      <button className="icon-btn" onClick={() => openEdit(s.id)} aria-label="Editar"><Pencil size={15} /></button>
                      <button className="icon-btn icon-btn-danger" onClick={() => remove.mutate(s.id)} aria-label="Desactivar"><Trash2 size={15} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr><td colSpan={7} className="cust-empty">
                  {isLoading ? 'Cargando…' : search ? 'Sin coincidencias.' : 'Aún no hay proveedores. Crea el primero.'}
                </td></tr>
              )}
            </tbody>
          </table>
        </div>
      </motion.div>

      <AnimatePresence>
        {editorOpen && (
          <>
            <motion.div className="drawer-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }}
              exit={{ opacity: 0 }} onClick={() => setEditorOpen(false)} />
            <motion.aside className="drawer" role="dialog" aria-label="Editor de proveedor"
              initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }}
              transition={{ type: 'spring', stiffness: 380, damping: 38 }}>
              <div className="drawer-head">
                <div>
                  <h3>{editingId ? 'Editar proveedor' : 'Nuevo proveedor'}</h3>
                  <span className="drawer-sub">{draft.name || 'Sin nombre'}</span>
                </div>
                <button className="icon-btn" onClick={() => setEditorOpen(false)}><X size={18} /></button>
              </div>

              <div className="drawer-tabs">
                <button className={`drawer-tab ${tab === 'general' ? 'is-active' : ''}`} onClick={() => setTab('general')}><Info size={15} /> Generales</button>
                <button className={`drawer-tab ${tab === 'address' ? 'is-active' : ''}`} onClick={() => setTab('address')}><MapPin size={15} /> Dirección</button>
                <button className={`drawer-tab ${tab === 'credit' ? 'is-active' : ''}`} onClick={() => setTab('credit')}><CreditCard size={15} /> Crédito</button>
                <button className={`drawer-tab ${tab === 'contacts' ? 'is-active' : ''}`} onClick={() => setTab('contacts')}><UsersIcon size={15} /> Contactos</button>
              </div>

              <div className="drawer-body">
                {tab === 'general' && (
                  <div className="drawer-section">
                    <div className="grid-2">
                      <label className="field"><span>Clave</span>
                        <input value={draft.externalCode} onChange={(e) => set('externalCode', e.target.value)} placeholder="000001" /></label>
                      <label className="field"><span>Clasificación</span>
                        <input value={draft.classification} onChange={(e) => set('classification', e.target.value)} /></label>
                    </div>
                    <label className="field"><span>Nombre *</span>
                      <input value={draft.name} onChange={(e) => set('name', e.target.value)} /></label>
                    <div className="grid-2">
                      <label className="field"><span>RFC</span>
                        <input value={draft.rfc} onChange={(e) => set('rfc', e.target.value.toUpperCase())} maxLength={13} /></label>
                      <label className="field"><span>Teléfono</span>
                        <input value={draft.phone} onChange={(e) => set('phone', e.target.value)} /></label>
                    </div>
                    <label className="field"><span>Email</span>
                      <input type="email" value={draft.email} onChange={(e) => set('email', e.target.value)} /></label>
                    <label className="field"><span>Revisión y pago</span>
                      <input value={draft.reviewPayment} onChange={(e) => set('reviewPayment', e.target.value)} placeholder="Ej. Lunes de revisión, jueves de pago" /></label>
                    <label className="field"><span>Observaciones</span>
                      <textarea rows={2} value={draft.notes} onChange={(e) => set('notes', e.target.value)} /></label>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.affectsInventoryOnly} onChange={(e) => set('affectsInventoryOnly', e.target.checked)} />
                      <span>Afectar compras sin inventario</span>
                    </label>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.skipPayable} onChange={(e) => set('skipPayable', e.target.checked)} />
                      <span>Afectar compras sin afectar cuentas por pagar</span>
                    </label>
                  </div>
                )}

                {tab === 'address' && (
                  <div className="drawer-section">
                    <div className="grid-2">
                      <label className="field"><span>País</span>
                        <input value={draft.country} onChange={(e) => set('country', e.target.value)} /></label>
                      <label className="field"><span>Código postal</span>
                        <input value={draft.zipCode} onChange={(e) => set('zipCode', e.target.value)} maxLength={5} /></label>
                    </div>
                    <label className="field"><span>Calle</span>
                      <input value={draft.street} onChange={(e) => set('street', e.target.value)} /></label>
                    <div className="grid-2">
                      <label className="field"><span>Colonia</span>
                        <input value={draft.neighborhood} onChange={(e) => set('neighborhood', e.target.value)} /></label>
                      <label className="field"><span>Población</span>
                        <input value={draft.town} onChange={(e) => set('town', e.target.value)} /></label>
                    </div>
                    <div className="grid-2">
                      <label className="field"><span>Ciudad</span>
                        <input value={draft.city} onChange={(e) => set('city', e.target.value)} /></label>
                      <label className="field"><span>Estado</span>
                        <input value={draft.state} onChange={(e) => set('state', e.target.value)} /></label>
                    </div>
                  </div>
                )}

                {tab === 'credit' && (
                  <div className="drawer-section">
                    <div className="grid-2">
                      <label className="field"><span>Días de crédito</span>
                        <input type="number" value={draft.creditDays} onChange={(e) => set('creditDays', e.target.value)} /></label>
                      <label className="field"><span>Límite de crédito</span>
                        <input type="number" step="0.01" value={draft.creditLimit} onChange={(e) => set('creditLimit', e.target.value)} /></label>
                    </div>
                    <p className="drawer-note" style={{ marginTop: 0 }}>Descuentos por volumen que ofrece el proveedor (%).</p>
                    <div className="grid-2">
                      <label className="field"><span>Descuento 1 %</span>
                        <input type="number" value={draft.discount1} onChange={(e) => set('discount1', e.target.value)} /></label>
                      <label className="field"><span>Descuento 2 %</span>
                        <input type="number" value={draft.discount2} onChange={(e) => set('discount2', e.target.value)} /></label>
                    </div>
                    <div className="grid-2">
                      <label className="field"><span>Descuento 3 %</span>
                        <input type="number" value={draft.discount3} onChange={(e) => set('discount3', e.target.value)} /></label>
                      <label className="field"><span>Descuento 4 %</span>
                        <input type="number" value={draft.discount4} onChange={(e) => set('discount4', e.target.value)} /></label>
                    </div>
                    <label className="field"><span>Descuento 5 %</span>
                      <input type="number" value={draft.discount5} onChange={(e) => set('discount5', e.target.value)} /></label>
                  </div>
                )}

                {tab === 'contacts' && (
                  <div className="drawer-section">
                    {draft.contacts.length === 0 && <p className="drawer-note" style={{ marginTop: 0 }}>Sin contactos.</p>}
                    {draft.contacts.map((c, i) => (
                      <div className="cust-address" key={i}>
                        <div className="cust-address-head">
                          <input className="cust-address-label" value={c.name} onChange={(e) => setContact(i, { name: e.target.value })} placeholder="Nombre del contacto" />
                          <button className="icon-btn icon-btn-danger" onClick={() => removeContact(i)} aria-label="Quitar"><Trash2 size={14} /></button>
                        </div>
                        <div className="grid-2">
                          <label className="field"><span>Puesto</span>
                            <input value={c.role} onChange={(e) => setContact(i, { role: e.target.value })} /></label>
                          <label className="field"><span>Teléfono</span>
                            <input value={c.phone} onChange={(e) => setContact(i, { phone: e.target.value })} /></label>
                        </div>
                        <label className="field"><span>Email</span>
                          <input value={c.email} onChange={(e) => setContact(i, { email: e.target.value })} /></label>
                      </div>
                    ))}
                    <button className="btn-ghost" onClick={addContact}><Plus size={15} /> Agregar contacto</button>
                  </div>
                )}
              </div>

              <div className="drawer-foot">
                <button className="btn-ghost" onClick={() => setEditorOpen(false)}>Cancelar</button>
                <motion.button className="btn-accent" disabled={!draft.name.trim() || save.isPending}
                  onClick={() => save.mutate()}
                  whileHover={!draft.name.trim() || save.isPending ? undefined : pressable.whileHover}
                  whileTap={!draft.name.trim() || save.isPending ? undefined : pressable.whileTap}
                  transition={pressable.transition}>
                  {save.isPending ? 'Guardando…' : editingId ? 'Guardar cambios' : 'Crear proveedor'}
                </motion.button>
              </div>
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {importOpen && (
          <ImportModal onClose={() => setImportOpen(false)}
            onDone={() => { setImportOpen(false); queryClient.invalidateQueries({ queryKey: ['suppliers'] }); }} />
        )}
      </AnimatePresence>
    </div>
  );
}

/** Importador de proveedores: pega filas (Nombre, RFC, Teléfono, Correo). */
function ImportModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const parsed = useMemo(() => text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean).map((l) => {
    const c = l.split(/\t|,|;/).map((x) => x.trim());
    return { name: c[0] ?? '', rfc: c[1] || null, phone: c[2] || null, email: c[3] || null };
  }).filter((c) => c.name), [text]);

  async function run() {
    if (!parsed.length) return;
    setBusy(true);
    try {
      const { data } = await api.post<{ created: number; failed: number }>('/purchasing/suppliers/import', { suppliers: parsed });
      toast.success('Importación completada', `${data.created} creado(s)${data.failed ? `, ${data.failed} con error` : ''}.`);
      onDone();
    } catch { toast.error('No se pudo importar'); } finally { setBusy(false); }
  }

  return (
    <motion.div className="pos-modal-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
      <motion.div className="pos-modal pos-modal-picker" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={{ type: 'spring', stiffness: 260, damping: 22 }}>
        <div className="pos-modal-head">
          <h3><Upload size={18} /> Importar proveedores</h3>
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        </div>
        <div style={{ padding: 'var(--space-5)' }}>
          <p className="drawer-note" style={{ marginTop: 0 }}>
            Pega filas desde Excel. Columnas: <strong>Nombre, RFC, Teléfono, Correo</strong>.
          </p>
          <textarea rows={8} value={text} onChange={(e) => setText(e.target.value)}
            placeholder={'Distribuidora del Norte\tDNO010101AAA\t8181818181\tventas@dno.com'}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: 13 }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--space-3)' }}>
            <span className="badge badge-muted">{parsed.length} fila(s)</span>
            <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <button className="btn-ghost" onClick={onClose}>Cancelar</button>
              <button className="btn-accent" disabled={!parsed.length || busy} onClick={run}>
                {busy ? 'Importando…' : `Importar ${parsed.length}`}
              </button>
            </div>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}
