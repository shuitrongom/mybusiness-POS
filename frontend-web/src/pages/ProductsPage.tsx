import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'motion/react';
import {
  Camera, Pencil, Trash2, X, Info, DollarSign, Boxes, SlidersHorizontal, Plus,
} from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fileToThumbnailDataUrl } from '@/lib/image';
import { fadeInUp, pressable, staggerContainer, staggerItem } from '@/lib/motion';
import '@/pages/dashboard.css';
import './products.css';

/** Campos profesionales del producto (coinciden con ProductExtras del backend). */
interface ProductExtras {
  description: string | null;
  brand: string | null;
  supplierId: number | null;
  taxRate: number;
  iepsRate: number;
  price2: number;
  price3: number;
  price4: number;
  price5: number;
  lastCost: number;
  avgCost: number;
  minStock: number;
  maxStock: number;
  reorderPoint: number;
  forSale: boolean;
  trackInventory: boolean;
  trackLots: boolean;
  allowBelowCost: boolean;
  blocked: boolean;
  isComposite: boolean;
  onSale: boolean;
  loyaltyPoints: number;
}

interface Product {
  id: number;
  sku: string | null;
  name: string;
  unit: string;
  soldByWeight: boolean;
  price: number;
  cost: number;
  active: boolean;
  barcodes: string[];
  imageUrl: string | null;
  extras: ProductExtras;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

const TILE_COLORS = ['#2e6ef2', '#12b886', '#f59e0b', '#e11d48', '#7c5cff', '#0ea5e9', '#16a34a', '#db2777'];
function colorFor(text: string): string {
  let hash = 0;
  for (let i = 0; i < text.length; i++) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  return TILE_COLORS[hash % TILE_COLORS.length];
}
function initials(name: string): string {
  const w = name.trim().split(/\s+/).filter(Boolean);
  if (w.length === 0) return '?';
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase();
}

/** Margen de utilidad en % dado precio y costo. */
function marginPct(price: number, cost: number): number | null {
  if (!price || price <= 0 || !cost || cost <= 0) return null;
  return ((price - cost) / price) * 100;
}

type Tab = 'general' | 'prices' | 'inventory' | 'advanced';

/** Estado editable del formulario (todo como string para inputs controlados). */
interface DraftForm {
  name: string;
  sku: string;
  description: string;
  brand: string;
  unit: string;
  soldByWeight: boolean;
  barcode: string;
  imageUrl: string;
  active: boolean;
  // precios y costos
  price: string;
  cost: string;
  lastCost: string;
  price2: string;
  price3: string;
  price4: string;
  price5: string;
  taxRate: string; // en porcentaje visible (16)
  iepsRate: string;
  // inventario
  minStock: string;
  maxStock: string;
  reorderPoint: string;
  // banderas
  forSale: boolean;
  trackInventory: boolean;
  trackLots: boolean;
  allowBelowCost: boolean;
  blocked: boolean;
  isComposite: boolean;
  onSale: boolean;
  loyaltyPoints: string;
}

function emptyDraft(): DraftForm {
  return {
    name: '', sku: '', description: '', brand: '', unit: 'pieza', soldByWeight: false,
    barcode: '', imageUrl: '', active: true,
    price: '', cost: '', lastCost: '', price2: '', price3: '', price4: '', price5: '',
    taxRate: '16', iepsRate: '0',
    minStock: '', maxStock: '', reorderPoint: '',
    forSale: true, trackInventory: true, trackLots: false, allowBelowCost: false,
    blocked: false, isComposite: false, onSale: false, loyaltyPoints: '',
  };
}

function draftFromProduct(p: Product): DraftForm {
  const x = p.extras;
  return {
    name: p.name, sku: p.sku ?? '', description: x.description ?? '', brand: x.brand ?? '',
    unit: p.unit, soldByWeight: p.soldByWeight, barcode: p.barcodes?.[0] ?? '',
    imageUrl: p.imageUrl ?? '', active: p.active,
    price: String(p.price ?? 0), cost: String(p.cost ?? 0), lastCost: String(x.lastCost ?? 0),
    price2: String(x.price2 ?? 0), price3: String(x.price3 ?? 0),
    price4: String(x.price4 ?? 0), price5: String(x.price5 ?? 0),
    taxRate: String((x.taxRate ?? 0.16) * 100), iepsRate: String((x.iepsRate ?? 0) * 100),
    minStock: String(x.minStock ?? 0), maxStock: String(x.maxStock ?? 0),
    reorderPoint: String(x.reorderPoint ?? 0),
    forSale: x.forSale, trackInventory: x.trackInventory, trackLots: x.trackLots,
    allowBelowCost: x.allowBelowCost, blocked: x.blocked, isComposite: x.isComposite,
    onSale: x.onSale, loyaltyPoints: String(x.loyaltyPoints ?? 0),
  };
}

/** Convierte el borrador al payload que espera el backend. */
function draftToPayload(d: DraftForm) {
  const num = (s: string) => Number(s) || 0;
  return {
    name: d.name.trim(),
    sku: d.sku.trim() || null,
    description: d.description.trim() || null,
    brand: d.brand.trim() || null,
    unit: d.unit,
    soldByWeight: d.soldByWeight,
    barcodes: d.barcode.trim() ? [d.barcode.trim()] : [],
    imageUrl: d.imageUrl || null,
    active: d.active,
    price: num(d.price),
    cost: num(d.cost),
    lastCost: num(d.lastCost),
    price2: num(d.price2),
    price3: num(d.price3),
    price4: num(d.price4),
    price5: num(d.price5),
    taxRate: num(d.taxRate) / 100,
    iepsRate: num(d.iepsRate) / 100,
    minStock: num(d.minStock),
    maxStock: num(d.maxStock),
    reorderPoint: num(d.reorderPoint),
    forSale: d.forSale,
    trackInventory: d.trackInventory,
    trackLots: d.trackLots,
    allowBelowCost: d.allowBelowCost,
    blocked: d.blocked,
    isComposite: d.isComposite,
    onSale: d.onSale,
    loyaltyPoints: num(d.loyaltyPoints),
  };
}

/**
 * Catálogo de productos con editor PREMIUM tipo "slide-over" (panel lateral) con pestañas:
 * General, Precios y costos (multiprecio + % utilidad), Inventario (min/max/reorden) y Avanzado
 * (banderas de comportamiento). Alta, edición y eliminación completas.
 */
export function ProductsPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [deleting, setDeleting] = useState<Product | null>(null);

  // Editor: null = cerrado; { id: null } = alta; { id } = edición.
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [draft, setDraft] = useState<DraftForm>(emptyDraft());
  const [tab, setTab] = useState<Tab>('general');

  const products = useQuery({
    queryKey: ['products', 'all'],
    queryFn: async () => (await api.get<Product[]>('/catalog/products')).data,
  });

  const save = useMutation({
    mutationFn: async () => {
      const payload = draftToPayload(draft);
      if (editingId) return api.put(`/catalog/products/${editingId}`, payload);
      return api.post('/catalog/products', payload);
    },
    onSuccess: () => {
      closeEditor();
      queryClient.invalidateQueries({ queryKey: ['products'] });
      toast.success(editingId ? 'Producto actualizado' : 'Producto creado',
        editingId ? 'Los cambios se guardaron.' : 'Ya está disponible en tu catálogo.');
    },
    onError: () => toast.error('No se pudo guardar el producto', 'Revisa los datos e inténtalo de nuevo.'),
  });

  const remove = useMutation({
    mutationFn: async (p: Product) =>
      (await api.delete<{ hardDeleted: boolean }>(`/catalog/products/${p.id}`)).data,
    onSuccess: (data) => {
      setDeleting(null);
      queryClient.invalidateQueries({ queryKey: ['products'] });
      if (data.hardDeleted) toast.success('Producto eliminado', 'Se borró del catálogo.');
      else toast.info('Producto desactivado', 'Tenía ventas registradas, así que se ocultó en vez de borrarse.');
    },
    onError: () => toast.error('No se pudo eliminar el producto'),
  });

  function openCreate() {
    setEditingId(null);
    setDraft(emptyDraft());
    setTab('general');
    setEditorOpen(true);
  }
  function openEdit(p: Product) {
    setEditingId(p.id);
    setDraft(draftFromProduct(p));
    setTab('general');
    setEditorOpen(true);
  }
  function closeEditor() {
    setEditorOpen(false);
    setEditingId(null);
  }
  const set = <K extends keyof DraftForm>(key: K, value: DraftForm[K]) =>
    setDraft((d) => ({ ...d, [key]: value }));

  const onPickImage = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const dataUrl = await fileToThumbnailDataUrl(file);
      set('imageUrl', dataUrl);
    } catch {
      toast.error('No se pudo procesar la imagen', 'Prueba con otra.');
    }
  };

  const filtered = (products.data ?? []).filter(
    (p) => query.trim() === '' || p.name.toLowerCase().includes(query.trim().toLowerCase()),
  );

  const margin = marginPct(Number(draft.price), Number(draft.cost));

  return (
    <div>
      <div className="prod-header">
        <div>
          <h1 className="page-title">Productos</h1>
          <p className="page-sub">Catálogo profesional de tu negocio</p>
        </div>
        <motion.button className="btn-accent" onClick={openCreate}
          whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
          <Plus size={17} /> Nuevo producto
        </motion.button>
      </div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
        variants={fadeInUp} initial="hidden" animate="visible">
        <div className="prod-list-head">
          <h3>Productos ({filtered.length})</h3>
          <input className="prod-search" value={query} onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar producto…" />
        </div>
        <table className="table">
          <thead>
            <tr><th></th><th>Producto</th><th>Unidad</th><th>Precio</th><th>Código</th><th>Acciones</th></tr>
          </thead>
          <motion.tbody variants={staggerContainer} initial="hidden" animate="visible">
            {filtered.map((p) => (
              <motion.tr key={p.id} variants={staggerItem} className={p.active ? '' : 'row-inactive'}>
                <td>
                  {p.imageUrl ? (
                    <img src={p.imageUrl} alt={p.name} className="prod-thumb" />
                  ) : (
                    <span className="prod-thumb prod-thumb-avatar" style={{ background: colorFor(p.name) }}>
                      {initials(p.name)}
                    </span>
                  )}
                </td>
                <td>
                  <strong>{p.name}</strong>
                  {!p.active && <span className="badge-muted">Inactivo</span>}
                  {p.extras?.onSale && <span className="badge-sale">Oferta</span>}
                </td>
                <td>{p.unit}</td>
                <td>{money(p.price)}</td>
                <td>{p.barcodes?.[0] ?? '—'}</td>
                <td>
                  <div className="prod-row-actions">
                    <button type="button" className="icon-btn" title="Editar" onClick={() => openEdit(p)}>
                      <Pencil size={18} color="#ffffff" strokeWidth={2.25} />
                    </button>
                    <button type="button" className="icon-btn icon-btn-danger" title="Eliminar"
                      onClick={() => setDeleting(p)}>
                      <Trash2 size={18} color="#ffffff" strokeWidth={2.25} />
                    </button>
                  </div>
                </td>
              </motion.tr>
            ))}
            {filtered.length === 0 && (
              <tr><td colSpan={6} className="empty">
                {products.data?.length === 0 ? 'Aún no hay productos.' : 'Sin resultados.'}
              </td></tr>
            )}
          </motion.tbody>
        </table>
      </motion.div>

      {/* Editor slide-over premium */}
      <AnimatePresence>
        {editorOpen && (
          <>
            <motion.div className="drawer-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }}
              exit={{ opacity: 0 }} onClick={closeEditor} />
            <motion.aside className="drawer" role="dialog" aria-label="Editor de producto"
              initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }}
              transition={{ type: 'spring', stiffness: 380, damping: 38 }}>
              <div className="drawer-head">
                <div>
                  <h3>{editingId ? 'Editar producto' : 'Nuevo producto'}</h3>
                  <span className="drawer-sub">{draft.name || 'Sin nombre'}</span>
                </div>
                <button className="icon-btn" onClick={closeEditor}><X size={18} /></button>
              </div>

              <div className="drawer-tabs">
                <button className={`drawer-tab ${tab === 'general' ? 'is-active' : ''}`}
                  onClick={() => setTab('general')}><Info size={15} /> General</button>
                <button className={`drawer-tab ${tab === 'prices' ? 'is-active' : ''}`}
                  onClick={() => setTab('prices')}><DollarSign size={15} /> Precios</button>
                <button className={`drawer-tab ${tab === 'inventory' ? 'is-active' : ''}`}
                  onClick={() => setTab('inventory')}><Boxes size={15} /> Inventario</button>
                <button className={`drawer-tab ${tab === 'advanced' ? 'is-active' : ''}`}
                  onClick={() => setTab('advanced')}><SlidersHorizontal size={15} /> Avanzado</button>
              </div>

              <div className="drawer-body">
                {tab === 'general' && (
                  <div className="drawer-section">
                    <div className="prod-photo prod-photo-inline">
                      <label className="prod-photo-drop">
                        {draft.imageUrl ? (
                          <img src={draft.imageUrl} alt="Vista previa" className="prod-photo-preview" />
                        ) : (
                          <span className="prod-photo-placeholder"><Camera size={26} strokeWidth={1.6} />Foto</span>
                        )}
                        <input type="file" accept="image/*" onChange={onPickImage} hidden />
                      </label>
                      {draft.imageUrl && (
                        <button className="prod-photo-clear" onClick={() => set('imageUrl', '')}>Quitar</button>
                      )}
                    </div>
                    <label className="field">
                      <span>Nombre *</span>
                      <input value={draft.name} onChange={(e) => set('name', e.target.value)}
                        placeholder="Coca-Cola 600 ml" />
                    </label>
                    <label className="field">
                      <span>Descripción</span>
                      <textarea value={draft.description} rows={2}
                        onChange={(e) => set('description', e.target.value)}
                        placeholder="Detalle del producto (opcional)" />
                    </label>
                    <div className="grid-2">
                      <label className="field">
                        <span>Marca</span>
                        <input value={draft.brand} onChange={(e) => set('brand', e.target.value)} placeholder="Coca-Cola" />
                      </label>
                      <label className="field">
                        <span>Clave interna (SKU)</span>
                        <input value={draft.sku} onChange={(e) => set('sku', e.target.value)} placeholder="ABC-001" />
                      </label>
                    </div>
                    <div className="grid-2">
                      <label className="field">
                        <span>Unidad</span>
                        <select value={draft.unit} onChange={(e) => set('unit', e.target.value)}>
                          <option value="pieza">Pieza</option>
                          <option value="kg">Kilogramo</option>
                          <option value="litro">Litro</option>
                          <option value="paquete">Paquete</option>
                        </select>
                      </label>
                      <label className="field">
                        <span>Código de barras</span>
                        <input value={draft.barcode} onChange={(e) => set('barcode', e.target.value)} placeholder="750..." />
                      </label>
                    </div>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.soldByWeight}
                        onChange={(e) => set('soldByWeight', e.target.checked)} />
                      <span>Se vende por peso (báscula)</span>
                    </label>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.active}
                        onChange={(e) => set('active', e.target.checked)} />
                      <span>Activo</span>
                    </label>
                  </div>
                )}

                {tab === 'prices' && (
                  <div className="drawer-section">
                    <div className="grid-2">
                      <label className="field">
                        <span>Costo</span>
                        <input type="number" step="0.01" value={draft.cost}
                          onChange={(e) => set('cost', e.target.value)} placeholder="0.00" />
                      </label>
                      <label className="field">
                        <span>Costo última compra</span>
                        <input type="number" step="0.01" value={draft.lastCost}
                          onChange={(e) => set('lastCost', e.target.value)} placeholder="0.00" />
                      </label>
                    </div>
                    <label className="field">
                      <span>Precio 1 · Público</span>
                      <input type="number" step="0.01" value={draft.price}
                        onChange={(e) => set('price', e.target.value)} placeholder="0.00" />
                    </label>
                    {margin !== null && (
                      <div className={`margin-hint ${margin < 0 ? 'is-neg' : ''}`}>
                        Utilidad: <strong>{margin.toFixed(1)}%</strong> ({money(Number(draft.price) - Number(draft.cost))} por unidad)
                      </div>
                    )}
                    <div className="grid-2">
                      <label className="field">
                        <span>Precio 2 · Mayoreo</span>
                        <input type="number" step="0.01" value={draft.price2}
                          onChange={(e) => set('price2', e.target.value)} placeholder="0.00" />
                      </label>
                      <label className="field">
                        <span>Precio 3 · Medio mayoreo</span>
                        <input type="number" step="0.01" value={draft.price3}
                          onChange={(e) => set('price3', e.target.value)} placeholder="0.00" />
                      </label>
                      <label className="field">
                        <span>Precio 4 · Especial</span>
                        <input type="number" step="0.01" value={draft.price4}
                          onChange={(e) => set('price4', e.target.value)} placeholder="0.00" />
                      </label>
                      <label className="field">
                        <span>Precio 5 · Distribuidor</span>
                        <input type="number" step="0.01" value={draft.price5}
                          onChange={(e) => set('price5', e.target.value)} placeholder="0.00" />
                      </label>
                    </div>
                    <div className="grid-2">
                      <label className="field">
                        <span>IVA (%)</span>
                        <input type="number" step="0.01" value={draft.taxRate}
                          onChange={(e) => set('taxRate', e.target.value)} placeholder="16" />
                      </label>
                      <label className="field">
                        <span>IEPS (%)</span>
                        <input type="number" step="0.01" value={draft.iepsRate}
                          onChange={(e) => set('iepsRate', e.target.value)} placeholder="0" />
                      </label>
                    </div>
                  </div>
                )}

                {tab === 'inventory' && (
                  <div className="drawer-section">
                    <p className="drawer-note">
                      El mínimo actúa como punto de reorden (cuándo comprar) y el máximo como nivel objetivo
                      (hasta cuánto reabastecer). Se usan para las alertas de inventario bajo.
                    </p>
                    <div className="grid-2">
                      <label className="field">
                        <span>Stock mínimo</span>
                        <input type="number" step="0.001" value={draft.minStock}
                          onChange={(e) => set('minStock', e.target.value)} placeholder="0" />
                      </label>
                      <label className="field">
                        <span>Stock máximo</span>
                        <input type="number" step="0.001" value={draft.maxStock}
                          onChange={(e) => set('maxStock', e.target.value)} placeholder="0" />
                      </label>
                      <label className="field">
                        <span>Punto de reorden</span>
                        <input type="number" step="0.001" value={draft.reorderPoint}
                          onChange={(e) => set('reorderPoint', e.target.value)} placeholder="0" />
                      </label>
                    </div>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.trackInventory}
                        onChange={(e) => set('trackInventory', e.target.checked)} />
                      <span>Controlar existencias de este producto</span>
                    </label>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.trackLots}
                        onChange={(e) => set('trackLots', e.target.checked)} />
                      <span>Controlar lotes y caducidad</span>
                    </label>
                  </div>
                )}

                {tab === 'advanced' && (
                  <div className="drawer-section">
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.forSale}
                        onChange={(e) => set('forSale', e.target.checked)} />
                      <span>Disponible para venta</span>
                    </label>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.onSale}
                        onChange={(e) => set('onSale', e.target.checked)} />
                      <span>En oferta</span>
                    </label>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.allowBelowCost}
                        onChange={(e) => set('allowBelowCost', e.target.checked)} />
                      <span>Permitir vender por debajo del costo</span>
                    </label>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.isComposite}
                        onChange={(e) => set('isComposite', e.target.checked)} />
                      <span>Artículo compuesto (kit / paquete)</span>
                    </label>
                    <label className="switch-row">
                      <input type="checkbox" checked={draft.blocked}
                        onChange={(e) => set('blocked', e.target.checked)} />
                      <span>Bloqueado (no operable)</span>
                    </label>
                    <label className="field">
                      <span>Puntos de lealtad que otorga</span>
                      <input type="number" step="0.01" value={draft.loyaltyPoints}
                        onChange={(e) => set('loyaltyPoints', e.target.value)} placeholder="0" />
                    </label>
                  </div>
                )}
              </div>

              <div className="drawer-foot">
                <button className="btn-ghost" onClick={closeEditor}>Cancelar</button>
                <motion.button className="btn-accent" disabled={!draft.name.trim() || save.isPending}
                  onClick={() => save.mutate()}
                  whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                  {save.isPending ? 'Guardando…' : editingId ? 'Guardar cambios' : 'Crear producto'}
                </motion.button>
              </div>
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      {/* Confirmación de borrado */}
      <AnimatePresence>
        {deleting && (
          <motion.div className="modal-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => setDeleting(null)}>
            <motion.div className="modal-card modal-card-sm" onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.94, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.96, y: 8 }} transition={{ duration: 0.18 }}>
              <div className="modal-head">
                <h3>Eliminar producto</h3>
                <button className="icon-btn" onClick={() => setDeleting(null)}><X size={18} /></button>
              </div>
              <p className="modal-text">
                ¿Seguro que quieres eliminar <strong>{deleting.name}</strong>? Si el producto tiene
                ventas registradas, se desactivará en lugar de borrarse para conservar el histórico.
              </p>
              <div className="modal-actions">
                <button className="btn-ghost" onClick={() => setDeleting(null)}>Cancelar</button>
                <motion.button className="btn-danger" disabled={remove.isPending}
                  onClick={() => remove.mutate(deleting)}
                  whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                  {remove.isPending ? 'Eliminando…' : 'Sí, eliminar'}
                </motion.button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
