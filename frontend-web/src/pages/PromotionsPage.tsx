import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'motion/react';
import { Tag, Plus, X, Pencil, Trash2, Percent, Gift, BadgePercent, Layers } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp, pressable } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/products.css';
import '@/pages/pos.css';
import '@/pages/customers.css';
import '@/pages/promotions.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Promotion {
  id: number;
  name: string;
  type: string;
  scope: string;
  categoryId: number | null;
  value: number;
  buyQty: number;
  payQty: number;
  minAmount: number;
  startsOn: string | null;
  endsOn: string | null;
  maxUses: number | null;
  usedCount: number;
  priority: number;
  active: boolean;
  productIds: number[];
}
interface Product { id: number; name: string; categoryId: number | null; }
interface Category { id: number; name: string; }

const TYPE_LABEL: Record<string, string> = {
  PERCENT: 'Porcentaje', AMOUNT: 'Importe fijo', SPECIAL_PRICE: 'Precio especial', NXM: 'NxM (2x1, 3x2…)',
};
const SCOPE_LABEL: Record<string, string> = { PRODUCT: 'Productos', CATEGORY: 'Categoría', ALL: 'Toda la tienda' };

interface Draft {
  name: string; type: string; scope: string; categoryId: string;
  value: string; buyQty: string; payQty: string; minAmount: string;
  startsOn: string; endsOn: string; maxUses: string; priority: string;
  active: boolean; productIds: number[];
}
function emptyDraft(): Draft {
  return {
    name: '', type: 'PERCENT', scope: 'PRODUCT', categoryId: '',
    value: '', buyQty: '2', payQty: '1', minAmount: '',
    startsOn: '', endsOn: '', maxUses: '', priority: '0',
    active: true, productIds: [],
  };
}
function draftFrom(p: Promotion): Draft {
  return {
    name: p.name, type: p.type, scope: p.scope, categoryId: p.categoryId ? String(p.categoryId) : '',
    value: String(p.value ?? 0), buyQty: String(p.buyQty || 2), payQty: String(p.payQty || 1),
    minAmount: String(p.minAmount ?? 0), startsOn: p.startsOn ?? '', endsOn: p.endsOn ?? '',
    maxUses: p.maxUses ? String(p.maxUses) : '', priority: String(p.priority ?? 0),
    active: p.active, productIds: p.productIds ?? [],
  };
}
function toPayload(d: Draft) {
  const num = (s: string) => (s === '' ? null : Number(s));
  return {
    name: d.name.trim(),
    type: d.type,
    scope: d.scope,
    categoryId: d.scope === 'CATEGORY' && d.categoryId ? Number(d.categoryId) : null,
    value: Number(d.value) || 0,
    buyQty: d.type === 'NXM' ? Number(d.buyQty) || 0 : 0,
    payQty: d.type === 'NXM' ? Number(d.payQty) || 0 : 0,
    minAmount: Number(d.minAmount) || 0,
    startsOn: d.startsOn || null,
    endsOn: d.endsOn || null,
    maxUses: num(d.maxUses),
    priority: Number(d.priority) || 0,
    active: d.active,
    productIds: d.scope === 'PRODUCT' ? d.productIds : [],
  };
}

/**
 * Gestor de PROMOCIONES: tabla de promociones + editor slide-over con tipo (porcentaje, importe,
 * precio especial, NxM), ámbito (productos/categoría/toda la tienda), vigencia y productos.
 * Las promociones activas se aplican automáticamente en el punto de venta.
 */
export function PromotionsPage() {
  const queryClient = useQueryClient();
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [draft, setDraft] = useState<Draft>(emptyDraft());
  const [productFilter, setProductFilter] = useState('');

  const { data: promos = [] } = useQuery({
    queryKey: ['promotions'],
    queryFn: async () => (await api.get<Promotion[]>('/promotions')).data,
  });
  const { data: products = [] } = useQuery({
    queryKey: ['catalog-products-min'],
    queryFn: async () => (await api.get<Product[]>('/catalog/products')).data,
  });
  const { data: categories = [] } = useQuery({
    queryKey: ['catalog-categories'],
    queryFn: async () => (await api.get<Category[]>('/catalog/products/categories')).data,
  });

  const save = useMutation({
    mutationFn: async () => {
      const payload = toPayload(draft);
      if (editingId) return api.put(`/promotions/${editingId}`, payload);
      return api.post('/promotions', payload);
    },
    onSuccess: () => {
      setEditorOpen(false);
      queryClient.invalidateQueries({ queryKey: ['promotions'] });
      toast.success(editingId ? 'Promoción actualizada' : 'Promoción creada', 'Se aplicará en el punto de venta.');
    },
    onError: () => toast.error('No se pudo guardar la promoción'),
  });
  const remove = useMutation({
    mutationFn: async (id: number) => api.delete(`/promotions/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] });
      toast.success('Promoción eliminada');
    },
    onError: () => toast.error('No se pudo eliminar la promoción'),
  });

  function openCreate() { setEditingId(null); setDraft(emptyDraft()); setEditorOpen(true); }
  function openEdit(p: Promotion) { setEditingId(p.id); setDraft(draftFrom(p)); setEditorOpen(true); }
  function set<K extends keyof Draft>(k: K, v: Draft[K]) { setDraft((d) => ({ ...d, [k]: v })); }
  function toggleProduct(id: number) {
    setDraft((d) => ({
      ...d,
      productIds: d.productIds.includes(id) ? d.productIds.filter((x) => x !== id) : [...d.productIds, id],
    }));
  }

  const filteredProducts = useMemo(() => {
    const q = productFilter.trim().toLowerCase();
    return products.filter((p) => q === '' || p.name.toLowerCase().includes(q)).slice(0, 100);
  }, [products, productFilter]);

  function describe(p: Promotion): string {
    if (p.type === 'PERCENT') return `${p.value}% de descuento`;
    if (p.type === 'AMOUNT') return `${money(p.value)} por unidad`;
    if (p.type === 'SPECIAL_PRICE') return `Precio especial ${money(p.value)}`;
    if (p.type === 'NXM') return `Lleva ${p.buyQty} paga ${p.payQty}`;
    return '';
  }

  return (
    <div>
      <div className="page-head-row">
        <div>
          <h1 className="page-title">Promociones</h1>
          <p className="page-sub">Descuentos, 2x1 y precios especiales que se aplican solos en el punto de venta</p>
        </div>
        <motion.button className="btn-accent" onClick={openCreate}
          whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
          <Plus size={16} /> Nueva promoción
        </motion.button>
      </div>

      <div className="promo-grid">
        {promos.map((p) => (
          <motion.div key={p.id} className={`promo-card ${p.active ? '' : 'is-off'}`}
            variants={fadeInUp} initial="hidden" animate="visible">
            <div className="promo-card-top">
              <span className="promo-icon">
                {p.type === 'PERCENT' ? <Percent size={18} />
                  : p.type === 'NXM' ? <Gift size={18} />
                    : p.type === 'SPECIAL_PRICE' ? <BadgePercent size={18} /> : <Tag size={18} />}
              </span>
              <div className="promo-card-actions">
                <button className="icon-btn" onClick={() => openEdit(p)} aria-label="Editar"><Pencil size={15} /></button>
                <button className="icon-btn icon-btn-danger" onClick={() => remove.mutate(p.id)} aria-label="Eliminar"><Trash2 size={15} /></button>
              </div>
            </div>
            <h3 className="promo-name">{p.name}</h3>
            <p className="promo-desc">{describe(p)}</p>
            <div className="promo-tags">
              <span className="promo-tag">{TYPE_LABEL[p.type]}</span>
              <span className="promo-tag promo-tag-scope">{SCOPE_LABEL[p.scope]}</span>
              {!p.active && <span className="promo-tag promo-tag-off">Inactiva</span>}
            </div>
            {(p.startsOn || p.endsOn) && (
              <p className="promo-dates">
                {p.startsOn || '…'} → {p.endsOn || '…'}
              </p>
            )}
          </motion.div>
        ))}
        {promos.length === 0 && (
          <div className="promo-empty">
            <Layers size={28} />
            <p>Aún no hay promociones. Crea la primera para que se aplique automáticamente al vender.</p>
          </div>
        )}
      </div>

      <AnimatePresence>
        {editorOpen && (
          <>
            <motion.div className="drawer-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }}
              exit={{ opacity: 0 }} onClick={() => setEditorOpen(false)} />
            <motion.aside className="drawer" role="dialog" aria-label="Editor de promoción"
              initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }}
              transition={{ type: 'spring', stiffness: 380, damping: 38 }}>
              <div className="drawer-head">
                <div>
                  <h3>{editingId ? 'Editar promoción' : 'Nueva promoción'}</h3>
                  <span className="drawer-sub">{draft.name || 'Sin nombre'}</span>
                </div>
                <button className="icon-btn" onClick={() => setEditorOpen(false)}><X size={18} /></button>
              </div>

              <div className="drawer-body">
                <div className="drawer-section">
                  <label className="field">
                    <span>Nombre de la promoción *</span>
                    <input value={draft.name} onChange={(e) => set('name', e.target.value)}
                      placeholder="Ej. Fin de semana 15% / 2x1 refrescos" />
                  </label>

                  <div className="grid-2">
                    <label className="field">
                      <span>Tipo</span>
                      <select value={draft.type} onChange={(e) => set('type', e.target.value)}>
                        <option value="PERCENT">Porcentaje de descuento</option>
                        <option value="AMOUNT">Importe fijo por unidad</option>
                        <option value="SPECIAL_PRICE">Precio especial</option>
                        <option value="NXM">NxM (2x1, 3x2…)</option>
                      </select>
                    </label>
                    <label className="field">
                      <span>Ámbito</span>
                      <select value={draft.scope} onChange={(e) => set('scope', e.target.value)}>
                        <option value="PRODUCT">Productos específicos</option>
                        <option value="CATEGORY">Una categoría</option>
                        <option value="ALL">Toda la tienda</option>
                      </select>
                    </label>
                  </div>

                  {draft.type === 'NXM' ? (
                    <div className="grid-2">
                      <label className="field"><span>Lleva (N)</span>
                        <input type="number" min={1} value={draft.buyQty} onChange={(e) => set('buyQty', e.target.value)} /></label>
                      <label className="field"><span>Paga (M)</span>
                        <input type="number" min={1} value={draft.payQty} onChange={(e) => set('payQty', e.target.value)} /></label>
                    </div>
                  ) : (
                    <label className="field">
                      <span>
                        {draft.type === 'PERCENT' ? 'Porcentaje (%)'
                          : draft.type === 'SPECIAL_PRICE' ? 'Precio especial' : 'Importe a descontar'}
                      </span>
                      <input type="number" min={0} step="0.01" value={draft.value}
                        onChange={(e) => set('value', e.target.value)} placeholder="0" />
                    </label>
                  )}

                  {draft.scope === 'CATEGORY' && (
                    <label className="field">
                      <span>Categoría</span>
                      <select value={draft.categoryId} onChange={(e) => set('categoryId', e.target.value)}>
                        <option value="">Selecciona…</option>
                        {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                      </select>
                    </label>
                  )}

                  <div className="grid-2">
                    <label className="field"><span>Compra mínima</span>
                      <input type="number" min={0} step="0.01" value={draft.minAmount}
                        onChange={(e) => set('minAmount', e.target.value)} placeholder="0" /></label>
                    <label className="field"><span>Prioridad</span>
                      <input type="number" value={draft.priority} onChange={(e) => set('priority', e.target.value)} /></label>
                  </div>

                  <div className="grid-2">
                    <label className="field"><span>Desde</span>
                      <input type="date" value={draft.startsOn} onChange={(e) => set('startsOn', e.target.value)} /></label>
                    <label className="field"><span>Hasta</span>
                      <input type="date" value={draft.endsOn} onChange={(e) => set('endsOn', e.target.value)} /></label>
                  </div>

                  <label className="switch-row">
                    <input type="checkbox" checked={draft.active} onChange={(e) => set('active', e.target.checked)} />
                    <span>Promoción activa</span>
                  </label>

                  {draft.scope === 'PRODUCT' && (
                    <div className="promo-products">
                      <div className="promo-products-head">
                        <span>Productos incluidos ({draft.productIds.length})</span>
                        <input value={productFilter} onChange={(e) => setProductFilter(e.target.value)}
                          placeholder="Filtrar productos…" />
                      </div>
                      <div className="promo-products-list">
                        {filteredProducts.map((p) => (
                          <label key={p.id} className="promo-product-item">
                            <input type="checkbox" checked={draft.productIds.includes(p.id)}
                              onChange={() => toggleProduct(p.id)} />
                            <span>{p.name}</span>
                          </label>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>

              <div className="drawer-foot">
                <button className="btn-ghost" onClick={() => setEditorOpen(false)}>Cancelar</button>
                <motion.button className="btn-accent" disabled={!draft.name.trim() || save.isPending}
                  onClick={() => save.mutate()}
                  whileHover={!draft.name.trim() || save.isPending ? undefined : pressable.whileHover}
                  whileTap={!draft.name.trim() || save.isPending ? undefined : pressable.whileTap}
                  transition={pressable.transition}>
                  {save.isPending ? 'Guardando…' : editingId ? 'Guardar cambios' : 'Crear promoción'}
                </motion.button>
              </div>
            </motion.aside>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}
