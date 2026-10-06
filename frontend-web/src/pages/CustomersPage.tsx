import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'motion/react';
import {
  Users, UserPlus, Search, X, Info, ReceiptText, CreditCard, MapPin,
  Camera, Trash2, Pencil, Upload, Plus, Phone, Mail,
} from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp, pressable } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/products.css';
import '@/pages/pos.css';
import '@/pages/customers.css';

const money = (n: number): string =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/** Régimen fiscal — claves oficiales del catálogo c_RegimenFiscal del SAT (CFDI 4.0). */
const TAX_REGIMES: { code: string; label: string }[] = [
  { code: '601', label: '601 · General de Ley Personas Morales' },
  { code: '603', label: '603 · Personas Morales con Fines no Lucrativos' },
  { code: '605', label: '605 · Sueldos y Salarios e Ingresos Asimilados a Salarios' },
  { code: '606', label: '606 · Arrendamiento' },
  { code: '608', label: '608 · Demás ingresos' },
  { code: '610', label: '610 · Residentes en el Extranjero sin Establecimiento Permanente' },
  { code: '611', label: '611 · Ingresos por Dividendos (socios y accionistas)' },
  { code: '612', label: '612 · Personas Físicas con Actividades Empresariales y Profesionales' },
  { code: '614', label: '614 · Ingresos por intereses' },
  { code: '616', label: '616 · Sin obligaciones fiscales' },
  { code: '620', label: '620 · Sociedades Cooperativas de Producción' },
  { code: '621', label: '621 · Incorporación Fiscal' },
  { code: '625', label: '625 · Actividades Empresariales con ingresos a través de Plataformas Tecnológicas' },
  { code: '626', label: '626 · Régimen Simplificado de Confianza (RESICO)' },
];

/** Uso de CFDI — claves oficiales del catálogo c_UsoCFDI del SAT (CFDI 4.0). */
const CFDI_USES: { code: string; label: string }[] = [
  { code: 'G01', label: 'G01 · Adquisición de mercancías' },
  { code: 'G03', label: 'G03 · Gastos en general' },
  { code: 'I01', label: 'I01 · Construcciones' },
  { code: 'I04', label: 'I04 · Equipo de cómputo y accesorios' },
  { code: 'D01', label: 'D01 · Honorarios médicos, dentales y gastos hospitalarios' },
  { code: 'D10', label: 'D10 · Pagos por servicios educativos (colegiaturas)' },
  { code: 'S01', label: 'S01 · Sin efectos fiscales' },
  { code: 'CP01', label: 'CP01 · Pagos' },
];

interface CustomerRow {
  id: number;
  name: string;
  rfc: string | null;
  phone: string | null;
  email: string | null;
  creditLimit: number;
  creditUsed: number;
  creditAvailable: number;
}

interface Address {
  id?: number | null;
  kind: string;
  label: string;
  street: string;
  extNumber: string;
  intNumber: string;
  neighborhood: string;
  city: string;
  state: string;
  zipCode: string;
  country: string;
  reference: string;
  isDefault: boolean;
}

interface CustomerDetail {
  id: number;
  name: string;
  legalName: string | null;
  personType: string;
  rfc: string | null;
  taxRegime: string | null;
  cfdiUse: string | null;
  zipCode: string | null;
  phone: string | null;
  mobile: string | null;
  email: string | null;
  contactName: string | null;
  salesperson: string | null;
  creditLimit: number;
  creditUsed: number;
  creditDays: number;
  defaultPriceList: number;
  classification: string | null;
  externalCode: string | null;
  notes: string | null;
  imageUrl: string | null;
  active: boolean;
  addresses: Address[];
}

interface DraftForm {
  name: string; legalName: string; personType: string; rfc: string;
  taxRegime: string; cfdiUse: string; zipCode: string;
  phone: string; mobile: string; email: string; contactName: string;
  salesperson: string; creditLimit: string; creditDays: string;
  defaultPriceList: string; classification: string; externalCode: string;
  notes: string; imageUrl: string; active: boolean; addresses: Address[];
}

function emptyDraft(): DraftForm {
  return {
    name: '', legalName: '', personType: 'FISICA', rfc: '',
    taxRegime: '', cfdiUse: 'G03', zipCode: '',
    phone: '', mobile: '', email: '', contactName: '',
    salesperson: '', creditLimit: '', creditDays: '',
    defaultPriceList: '1', classification: '', externalCode: '',
    notes: '', imageUrl: '', active: true, addresses: [],
  };
}

function draftFromDetail(c: CustomerDetail): DraftForm {
  return {
    name: c.name, legalName: c.legalName ?? '', personType: c.personType || 'FISICA',
    rfc: c.rfc ?? '', taxRegime: c.taxRegime ?? '', cfdiUse: c.cfdiUse ?? 'G03',
    zipCode: c.zipCode ?? '', phone: c.phone ?? '', mobile: c.mobile ?? '',
    email: c.email ?? '', contactName: c.contactName ?? '', salesperson: c.salesperson ?? '',
    creditLimit: String(c.creditLimit ?? 0), creditDays: String(c.creditDays ?? 0),
    defaultPriceList: String(c.defaultPriceList ?? 1), classification: c.classification ?? '',
    externalCode: c.externalCode ?? '', notes: c.notes ?? '', imageUrl: c.imageUrl ?? '',
    active: c.active, addresses: c.addresses ?? [],
  };
}

function toPayload(d: DraftForm) {
  const num = (s: string) => (s === '' ? null : Number(s));
  return {
    name: d.name.trim(),
    legalName: d.legalName.trim() || null,
    personType: d.personType,
    rfc: d.rfc.trim().toUpperCase() || null,
    taxRegime: d.taxRegime || null,
    cfdiUse: d.cfdiUse || null,
    zipCode: d.zipCode.trim() || null,
    phone: d.phone.trim() || null,
    mobile: d.mobile.trim() || null,
    email: d.email.trim() || null,
    contactName: d.contactName.trim() || null,
    salesperson: d.salesperson.trim() || null,
    creditLimit: num(d.creditLimit),
    creditDays: num(d.creditDays),
    defaultPriceList: Number(d.defaultPriceList) || 1,
    classification: d.classification.trim() || null,
    externalCode: d.externalCode.trim() || null,
    notes: d.notes.trim() || null,
    imageUrl: d.imageUrl || null,
    active: d.active,
    addresses: d.addresses,
  };
}

const TILE_COLORS = ['#2e6ef2', '#12b886', '#f59e0b', '#e11d48', '#7c5cff', '#0ea5e9'];
function colorFor(t: string): string {
  let h = 0; for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) >>> 0;
  return TILE_COLORS[h % TILE_COLORS.length];
}
function initials(name: string): string {
  const w = name.trim().split(/\s+/).filter(Boolean);
  if (!w.length) return '?';
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase();
}

function emptyAddress(): Address {
  return {
    kind: 'FISCAL', label: '', street: '', extNumber: '', intNumber: '',
    neighborhood: '', city: '', state: '', zipCode: '', country: 'México',
    reference: '', isDefault: true,
  };
}

type Tab = 'general' | 'fiscal' | 'credit' | 'addresses';

/**
 * Módulo de Clientes ENTERPRISE: tabla premium con búsqueda + editor slide-over con pestañas
 * (Generales, Fiscal CFDI 4.0, Crédito y Direcciones), foto de cliente e importación en lote.
 */
export function CustomersPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [tab, setTab] = useState<Tab>('general');
  const [draft, setDraft] = useState<DraftForm>(emptyDraft());
  const [importOpen, setImportOpen] = useState(false);

  const { data: customers = [], isLoading } = useQuery({
    queryKey: ['customers', search],
    queryFn: async () =>
      (await api.get<CustomerRow[]>('/customers', { params: { q: search, limit: 200 } })).data,
  });

  const save = useMutation({
    mutationFn: async () => {
      const payload = toPayload(draft);
      if (editingId) return api.put(`/customers/${editingId}`, payload);
      return api.post('/customers/full', payload);
    },
    onSuccess: () => {
      closeEditor();
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      toast.success(editingId ? 'Cliente actualizado' : 'Cliente creado',
        'Los datos se guardaron correctamente.');
    },
    onError: () => toast.error('No se pudo guardar el cliente', 'Revisa los datos e inténtalo de nuevo.'),
  });

  const remove = useMutation({
    mutationFn: async (id: number) => api.delete(`/customers/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      toast.success('Cliente desactivado', 'Se conserva su histórico de ventas y crédito.');
    },
    onError: () => toast.error('No se pudo desactivar el cliente'),
  });

  function openCreate() {
    setEditingId(null);
    setDraft(emptyDraft());
    setTab('general');
    setEditorOpen(true);
  }

  async function openEdit(id: number) {
    try {
      const { data } = await api.get<CustomerDetail>(`/customers/${id}`);
      setEditingId(id);
      setDraft(draftFromDetail(data));
      setTab('general');
      setEditorOpen(true);
    } catch {
      toast.error('No se pudo cargar el cliente');
    }
  }

  function closeEditor() {
    setEditorOpen(false);
    setEditingId(null);
  }

  function set<K extends keyof DraftForm>(key: K, value: DraftForm[K]) {
    setDraft((d) => ({ ...d, [key]: value }));
  }

  function onPhoto(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => set('imageUrl', String(reader.result));
    reader.readAsDataURL(file);
  }

  function addAddress() {
    setDraft((d) => ({ ...d, addresses: [...d.addresses, emptyAddress()] }));
  }
  function setAddress(i: number, patch: Partial<Address>) {
    setDraft((d) => ({ ...d, addresses: d.addresses.map((a, idx) => (idx === i ? { ...a, ...patch } : a)) }));
  }
  function removeAddress(i: number) {
    setDraft((d) => ({ ...d, addresses: d.addresses.filter((_, idx) => idx !== i) }));
  }

  const rows = useMemo(() => customers, [customers]);

  return (
    <div>
      <div className="page-head-row">
        <div>
          <h1 className="page-title">Clientes</h1>
          <p className="page-sub">Directorio de clientes, datos fiscales CFDI 4.0 y crédito</p>
        </div>
        <div className="page-head-actions">
          <button className="btn-ghost" onClick={() => setImportOpen(true)}>
            <Upload size={16} /> Importar
          </button>
          <motion.button className="btn-accent" onClick={openCreate}
            whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
            <UserPlus size={16} /> Nuevo cliente
          </motion.button>
        </div>
      </div>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="cust-toolbar">
          <div className="cust-search">
            <Search size={16} />
            <input value={search} onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por nombre, RFC, teléfono o correo…" />
          </div>
          <span className="cust-count"><Users size={15} /> {rows.length} cliente(s)</span>
        </div>

        <div className="cust-table-wrap">
          <table className="cust-table">
            <thead>
              <tr>
                <th>Cliente</th>
                <th>RFC</th>
                <th>Contacto</th>
                <th className="ta-right">Límite crédito</th>
                <th className="ta-right">Disponible</th>
                <th className="ta-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id}>
                  <td>
                    <div className="cust-cell">
                      <span className="cust-avatar" style={{ background: colorFor(c.name) }}>
                        {initials(c.name)}
                      </span>
                      <strong>{c.name}</strong>
                    </div>
                  </td>
                  <td>{c.rfc || <span className="muted">—</span>}</td>
                  <td>
                    <div className="cust-contact">
                      {c.phone && <span><Phone size={12} /> {c.phone}</span>}
                      {c.email && <span><Mail size={12} /> {c.email}</span>}
                      {!c.phone && !c.email && <span className="muted">—</span>}
                    </div>
                  </td>
                  <td className="ta-right">{money(c.creditLimit)}</td>
                  <td className="ta-right">
                    <span className={c.creditAvailable > 0 ? 'cust-credit-ok' : 'cust-credit-none'}>
                      {money(c.creditAvailable)}
                    </span>
                  </td>
                  <td className="ta-right">
                    <div className="cust-actions">
                      <button className="icon-btn" onClick={() => openEdit(c.id)} aria-label="Editar">
                        <Pencil size={15} />
                      </button>
                      <button className="icon-btn icon-btn-danger" onClick={() => remove.mutate(c.id)} aria-label="Desactivar">
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="cust-empty">
                    {isLoading ? 'Cargando…'
                      : search ? 'Ningún cliente coincide con la búsqueda.'
                        : 'Aún no hay clientes. Crea el primero con "Nuevo cliente".'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </motion.div>

      {/* Editor slide-over premium */}
      <AnimatePresence>
        {editorOpen && (
          <>
            <motion.div className="drawer-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }}
              exit={{ opacity: 0 }} onClick={closeEditor} />
            <motion.aside className="drawer" role="dialog" aria-label="Editor de cliente"
              initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }}
              transition={{ type: 'spring', stiffness: 380, damping: 38 }}>
              <div className="drawer-head">
                <div>
                  <h3>{editingId ? 'Editar cliente' : 'Nuevo cliente'}</h3>
                  <span className="drawer-sub">{draft.name || 'Sin nombre'}</span>
                </div>
                <button className="icon-btn" onClick={closeEditor}><X size={18} /></button>
              </div>

              <div className="drawer-tabs">
                <button className={`drawer-tab ${tab === 'general' ? 'is-active' : ''}`}
                  onClick={() => setTab('general')}><Info size={15} /> Generales</button>
                <button className={`drawer-tab ${tab === 'fiscal' ? 'is-active' : ''}`}
                  onClick={() => setTab('fiscal')}><ReceiptText size={15} /> Fiscal</button>
                <button className={`drawer-tab ${tab === 'credit' ? 'is-active' : ''}`}
                  onClick={() => setTab('credit')}><CreditCard size={15} /> Crédito</button>
                <button className={`drawer-tab ${tab === 'addresses' ? 'is-active' : ''}`}
                  onClick={() => setTab('addresses')}><MapPin size={15} /> Direcciones</button>
              </div>

              <div className="drawer-body">
                {tab === 'general' && (
                  <div className="drawer-section">
                    <div className="cust-photo-row">
                      <label className="cust-photo-drop">
                        {draft.imageUrl ? (
                          <img src={draft.imageUrl} alt="Foto del cliente" />
                        ) : (
                          <span className="cust-photo-avatar" style={{ background: colorFor(draft.name || '?') }}>
                            {draft.name ? initials(draft.name) : <Camera size={22} />}
                          </span>
                        )}
                        <input type="file" accept="image/*" onChange={onPhoto} hidden />
                        <span className="cust-photo-badge"><Camera size={13} /></span>
                      </label>
                      <div className="cust-photo-hint">
                        <strong>Foto o logo del cliente</strong>
                        <span>Se muestra en el directorio y en los tickets a crédito.</span>
                        {draft.imageUrl && (
                          <button className="btn-ghost btn-sm" onClick={() => set('imageUrl', '')}>
                            <Trash2 size={13} /> Quitar
                          </button>
                        )}
                      </div>
                    </div>

                    <label className="field">
                      <span>Nombre comercial *</span>
                      <input value={draft.name} onChange={(e) => set('name', e.target.value)}
                        placeholder="Nombre con el que conoces al cliente" />
                    </label>
                    <div className="grid-2">
                      <label className="field">
                        <span>Teléfono</span>
                        <input value={draft.phone} onChange={(e) => set('phone', e.target.value)} />
                      </label>
                      <label className="field">
                        <span>Celular</span>
                        <input value={draft.mobile} onChange={(e) => set('mobile', e.target.value)} />
                      </label>
                    </div>
                    <div className="grid-2">
                      <label className="field">
                        <span>Correo</span>
                        <input type="email" value={draft.email} onChange={(e) => set('email', e.target.value)} />
                      </label>
                      <label className="field">
                        <span>Contacto</span>
                        <input value={draft.contactName} onChange={(e) => set('contactName', e.target.value)} />
                      </label>
                    </div>
                    <div className="grid-2">
                      <label className="field">
                        <span>Vendedor asignado</span>
                        <input value={draft.salesperson} onChange={(e) => set('salesperson', e.target.value)} />
                      </label>
                      <label className="field">
                        <span>Clasificación</span>
                        <input value={draft.classification} onChange={(e) => set('classification', e.target.value)}
                          placeholder="VIP, Mayorista, Frecuente…" />
                      </label>
                    </div>
                  </div>
                )}

                {tab === 'fiscal' && (
                  <div className="drawer-section">
                    <p className="drawer-note">
                      Datos requeridos para timbrar CFDI 4.0. La razón social, el código postal, el
                      régimen fiscal y el uso de CFDI deben coincidir con la Constancia de Situación Fiscal.
                    </p>
                    <div className="grid-2">
                      <label className="field">
                        <span>Tipo de persona</span>
                        <select value={draft.personType} onChange={(e) => set('personType', e.target.value)}>
                          <option value="FISICA">Persona física</option>
                          <option value="MORAL">Persona moral</option>
                        </select>
                      </label>
                      <label className="field">
                        <span>RFC</span>
                        <input value={draft.rfc} onChange={(e) => set('rfc', e.target.value.toUpperCase())}
                          maxLength={13} placeholder={draft.personType === 'MORAL' ? 'ABC123456XYZ' : 'XAXX010101000'} />
                      </label>
                    </div>
                    <label className="field">
                      <span>Razón social (nombre fiscal exacto)</span>
                      <input value={draft.legalName} onChange={(e) => set('legalName', e.target.value)}
                        placeholder="Como aparece en la Constancia de Situación Fiscal" />
                    </label>
                    <div className="grid-2">
                      <label className="field">
                        <span>Régimen fiscal</span>
                        <select value={draft.taxRegime} onChange={(e) => set('taxRegime', e.target.value)}>
                          <option value="">Selecciona…</option>
                          {TAX_REGIMES.map((r) => <option key={r.code} value={r.code}>{r.label}</option>)}
                        </select>
                      </label>
                      <label className="field">
                        <span>Uso de CFDI</span>
                        <select value={draft.cfdiUse} onChange={(e) => set('cfdiUse', e.target.value)}>
                          {CFDI_USES.map((u) => <option key={u.code} value={u.code}>{u.label}</option>)}
                        </select>
                      </label>
                    </div>
                    <label className="field">
                      <span>Código postal del domicilio fiscal</span>
                      <input value={draft.zipCode} onChange={(e) => set('zipCode', e.target.value)}
                        maxLength={5} placeholder="00000" />
                    </label>
                  </div>
                )}

                {tab === 'credit' && (
                  <div className="drawer-section">
                    <p className="drawer-note">
                      El límite de crédito y el plazo controlan las ventas a crédito en el punto de venta.
                      El crédito utilizado se actualiza automáticamente con las cuentas por cobrar.
                    </p>
                    <div className="grid-2">
                      <label className="field">
                        <span>Límite de crédito</span>
                        <input type="number" step="0.01" value={draft.creditLimit}
                          onChange={(e) => set('creditLimit', e.target.value)} placeholder="0.00" />
                      </label>
                      <label className="field">
                        <span>Días de crédito</span>
                        <input type="number" value={draft.creditDays}
                          onChange={(e) => set('creditDays', e.target.value)} placeholder="0" />
                      </label>
                    </div>
                    <div className="grid-2">
                      <label className="field">
                        <span>Lista de precio del cliente</span>
                        <select value={draft.defaultPriceList} onChange={(e) => set('defaultPriceList', e.target.value)}>
                          <option value="1">1 · Público</option>
                          <option value="2">2 · Mayoreo</option>
                          <option value="3">3 · Medio mayoreo</option>
                          <option value="4">4 · Especial</option>
                          <option value="5">5 · Distribuidor</option>
                        </select>
                      </label>
                      <label className="field">
                        <span>Código de cliente</span>
                        <input value={draft.externalCode} onChange={(e) => set('externalCode', e.target.value)} />
                      </label>
                    </div>
                    <label className="field">
                      <span>Notas</span>
                      <textarea rows={3} value={draft.notes} onChange={(e) => set('notes', e.target.value)} />
                    </label>
                    {editingId && (
                      <label className="switch-row">
                        <input type="checkbox" checked={draft.active}
                          onChange={(e) => set('active', e.target.checked)} />
                        <span>Cliente activo</span>
                      </label>
                    )}
                  </div>
                )}

                {tab === 'addresses' && (
                  <div className="drawer-section">
                    {draft.addresses.length === 0 && (
                      <p className="drawer-note">Sin direcciones. Agrega la fiscal y las de envío.</p>
                    )}
                    {draft.addresses.map((a, i) => (
                      <div className="cust-address" key={i}>
                        <div className="cust-address-head">
                          <select value={a.kind} onChange={(e) => setAddress(i, { kind: e.target.value })}>
                            <option value="FISCAL">Fiscal</option>
                            <option value="SHIPPING">Envío</option>
                            <option value="BRANCH">Sucursal</option>
                          </select>
                          <input className="cust-address-label" value={a.label}
                            onChange={(e) => setAddress(i, { label: e.target.value })} placeholder="Alias (Matriz, Bodega…)" />
                          <button className="icon-btn icon-btn-danger" onClick={() => removeAddress(i)} aria-label="Quitar">
                            <Trash2 size={14} />
                          </button>
                        </div>
                        <div className="grid-2">
                          <label className="field"><span>Calle</span>
                            <input value={a.street} onChange={(e) => setAddress(i, { street: e.target.value })} /></label>
                          <div className="grid-2">
                            <label className="field"><span>No. ext</span>
                              <input value={a.extNumber} onChange={(e) => setAddress(i, { extNumber: e.target.value })} /></label>
                            <label className="field"><span>No. int</span>
                              <input value={a.intNumber} onChange={(e) => setAddress(i, { intNumber: e.target.value })} /></label>
                          </div>
                        </div>
                        <div className="grid-2">
                          <label className="field"><span>Colonia</span>
                            <input value={a.neighborhood} onChange={(e) => setAddress(i, { neighborhood: e.target.value })} /></label>
                          <label className="field"><span>C.P.</span>
                            <input value={a.zipCode} maxLength={5} onChange={(e) => setAddress(i, { zipCode: e.target.value })} /></label>
                        </div>
                        <div className="grid-2">
                          <label className="field"><span>Ciudad / Municipio</span>
                            <input value={a.city} onChange={(e) => setAddress(i, { city: e.target.value })} /></label>
                          <label className="field"><span>Estado</span>
                            <input value={a.state} onChange={(e) => setAddress(i, { state: e.target.value })} /></label>
                        </div>
                        <label className="field"><span>Referencias</span>
                          <input value={a.reference} onChange={(e) => setAddress(i, { reference: e.target.value })} /></label>
                      </div>
                    ))}
                    <button className="btn-ghost" onClick={addAddress}><Plus size={15} /> Agregar dirección</button>
                  </div>
                )}
              </div>

              <div className="drawer-foot">
                <button className="btn-ghost" onClick={closeEditor}>Cancelar</button>
                <motion.button className="btn-accent" disabled={!draft.name.trim() || save.isPending}
                  onClick={() => save.mutate()}
                  whileHover={!draft.name.trim() || save.isPending ? undefined : pressable.whileHover}
                  whileTap={!draft.name.trim() || save.isPending ? undefined : pressable.whileTap}
                  transition={pressable.transition}>
                  {save.isPending ? 'Guardando…' : editingId ? 'Guardar cambios' : 'Crear cliente'}
                </motion.button>
              </div>
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {importOpen && (
          <ImportModal
            onClose={() => setImportOpen(false)}
            onDone={() => { setImportOpen(false); queryClient.invalidateQueries({ queryKey: ['customers'] }); }}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

/**
 * Importador de clientes: pega filas del Excel (tabuladas). Formato de columnas:
 * Nombre, RFC, Teléfono, Correo, Límite de crédito.
 */
function ImportModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);

  const parsed = useMemo(() => {
    return text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map((line) => {
      const cols = line.split(/\t|,|;/).map((c) => c.trim());
      return {
        name: cols[0] ?? '',
        rfc: cols[1] || null,
        phone: cols[2] || null,
        email: cols[3] || null,
        creditLimit: cols[4] ? Number(cols[4].replace(/[^0-9.]/g, '')) : null,
      };
    }).filter((c) => c.name);
  }, [text]);

  async function run() {
    if (parsed.length === 0) return;
    setBusy(true);
    try {
      const { data } = await api.post<{ created: number; failed: number; total: number }>(
        '/customers/import', { customers: parsed });
      toast.success('Importación completada',
        `${data.created} creado(s)${data.failed ? `, ${data.failed} con error` : ''}.`);
      onDone();
    } catch {
      toast.error('No se pudo importar', 'Revisa el formato de las filas.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <motion.div className="pos-modal-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
      <motion.div className="pos-modal pos-modal-picker" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={{ type: 'spring', stiffness: 260, damping: 22 }}>
        <div className="pos-modal-head">
          <h3><Upload size={18} /> Importar clientes</h3>
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        </div>
        <div style={{ padding: 'var(--space-5)' }}>
          <p className="drawer-note" style={{ marginTop: 0 }}>
            Copia las filas desde Excel y pégalas aquí. Columnas por fila (separadas por tabulador,
            coma o punto y coma): <strong>Nombre, RFC, Teléfono, Correo, Límite de crédito</strong>.
          </p>
          <textarea rows={8} value={text} onChange={(e) => setText(e.target.value)}
            placeholder={'Abarrotes La Esquina\tABC123456XYZ\t5512345678\tcorreo@dominio.com\t5000'}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: 13 }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--space-3)' }}>
            <span className="badge badge-muted">{parsed.length} fila(s) detectada(s)</span>
            <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <button className="btn-ghost" onClick={onClose}>Cancelar</button>
              <button className="btn-accent" disabled={parsed.length === 0 || busy} onClick={run}>
                {busy ? 'Importando…' : `Importar ${parsed.length}`}
              </button>
            </div>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}
