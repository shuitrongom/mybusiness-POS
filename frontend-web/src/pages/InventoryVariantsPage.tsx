import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { Ruler, Wand2 } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/inventory.css';

interface Size { id: number; code: string; label: string; }
interface Color { id: number; code: string; label: string; hex: string | null; }
interface Category { id: number; name: string; }

/**
 * Tallas y colores: define un modelo (marca, costo, precios) y elige qué tallas y colores aplican.
 * "Generar códigos" crea un producto (variante) por cada combinación talla × color, cada uno con
 * su SKU y código de barras. Estándar de retail de ropa/calzado (una variante = un SKU).
 */
export function InventoryVariantsPage() {
  const [sizes, setSizes] = useState<Size[]>([]);
  const [colors, setColors] = useState<Color[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [model, setModel] = useState({ code: '', name: '', brand: '', cost: '', price1: '', price2: '', price3: '', categoryId: '' });
  const [selSizes, setSelSizes] = useState<string[]>([]);
  const [selColors, setSelColors] = useState<string[]>([]);
  const [newSize, setNewSize] = useState({ code: '', label: '' });
  const [newColor, setNewColor] = useState({ code: '', label: '', hex: '#2563eb' });
  const [busy, setBusy] = useState(false);

  const loadCatalogs = () => {
    api.get<Size[]>('/inventory/sizes').then((r) => setSizes(r.data)).catch(() => {});
    api.get<Color[]>('/inventory/colors').then((r) => setColors(r.data)).catch(() => {});
  };

  useEffect(() => {
    loadCatalogs();
    api.get<Category[]>('/catalog/products/categories').then((r) => setCategories(r.data)).catch(() => {});
  }, []);

  const addSize = async () => {
    if (!newSize.code.trim() || !newSize.label.trim()) { toast.info('Escribe clave y descripción de la talla'); return; }
    try {
      await api.post('/inventory/sizes', { code: newSize.code.trim(), label: newSize.label.trim() });
      setNewSize({ code: '', label: '' });
      loadCatalogs();
      toast.success('Talla agregada');
    } catch { toast.error('No se pudo agregar la talla'); }
  };
  const addColor = async () => {
    if (!newColor.code.trim() || !newColor.label.trim()) { toast.info('Escribe clave y descripción del color'); return; }
    try {
      await api.post('/inventory/colors', { code: newColor.code.trim(), label: newColor.label.trim(), hex: newColor.hex });
      setNewColor({ code: '', label: '', hex: '#2563eb' });
      loadCatalogs();
      toast.success('Color agregado');
    } catch { toast.error('No se pudo agregar el color'); }
  };

  const toggle = (arr: string[], set: (v: string[]) => void, code: string) =>
    set(arr.includes(code) ? arr.filter((x) => x !== code) : [...arr, code]);

  const combos = selSizes.length * selColors.length;

  const generate = async () => {
    if (!model.code.trim() || !model.name.trim()) { toast.info('Escribe clave y nombre del modelo'); return; }
    if (combos === 0) { toast.info('Elige al menos una talla y un color'); return; }
    setBusy(true);
    try {
      const { data } = await api.post<{ variantsCreated: number }>('/inventory/variants/generate', {
        modelCode: model.code.trim(), modelName: model.name.trim(), brand: model.brand.trim() || null,
        cost: model.cost ? Number(model.cost) : 0,
        price1: model.price1 ? Number(model.price1) : 0,
        price2: model.price2 ? Number(model.price2) : 0,
        price3: model.price3 ? Number(model.price3) : 0,
        categoryId: model.categoryId ? Number(model.categoryId) : null,
        sizeCodes: selSizes, colorCodes: selColors,
      });
      toast.success('Variantes generadas', `Se crearon ${data.variantsCreated} producto(s).`);
      setSelSizes([]); setSelColors([]);
    } catch { toast.error('No se pudieron generar las variantes'); } finally { setBusy(false); }
  };

  return (
    <div>
      <h1 className="page-title">Tallas y colores</h1>
      <p className="page-sub">Genera un producto por cada combinación talla × color de un modelo</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Ruler size={18} /> Modelo</h3>
        <div className="drawer-section" style={{ marginTop: 'var(--space-3)' }}>
          <div className="grid-2">
            <label className="field"><span>Clave del modelo *</span>
              <input value={model.code} onChange={(e) => setModel({ ...model, code: e.target.value })} placeholder="Ej. PLAYERA-BASIC" /></label>
            <label className="field"><span>Nombre *</span>
              <input value={model.name} onChange={(e) => setModel({ ...model, name: e.target.value })} /></label>
          </div>
          <div className="grid-2">
            <label className="field"><span>Marca</span>
              <input value={model.brand} onChange={(e) => setModel({ ...model, brand: e.target.value })} /></label>
            <label className="field"><span>Categoría</span>
              <select value={model.categoryId} onChange={(e) => setModel({ ...model, categoryId: e.target.value })}>
                <option value="">Sin categoría</option>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select></label>
          </div>
          <div className="grid-2">
            <label className="field"><span>Costo</span>
              <input type="number" step="0.01" value={model.cost} onChange={(e) => setModel({ ...model, cost: e.target.value })} /></label>
            <label className="field"><span>Precio 1</span>
              <input type="number" step="0.01" value={model.price1} onChange={(e) => setModel({ ...model, price1: e.target.value })} /></label>
          </div>
          <div className="grid-2">
            <label className="field"><span>Precio 2</span>
              <input type="number" step="0.01" value={model.price2} onChange={(e) => setModel({ ...model, price2: e.target.value })} /></label>
            <label className="field"><span>Precio 3</span>
              <input type="number" step="0.01" value={model.price3} onChange={(e) => setModel({ ...model, price3: e.target.value })} /></label>
          </div>
        </div>
      </motion.div>

      <div className="var-grids">
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title">Tallas ({selSizes.length})</h3>
          <div className="var-chips">
            {sizes.map((s) => (
              <button key={s.id} className={`var-chip ${selSizes.includes(s.code) ? 'is-on' : ''}`}
                onClick={() => toggle(selSizes, setSelSizes, s.code)}>{s.label}</button>
            ))}
          </div>
          <div className="var-add">
            <input value={newSize.code} onChange={(e) => setNewSize({ ...newSize, code: e.target.value })} placeholder="Clave" />
            <input value={newSize.label} onChange={(e) => setNewSize({ ...newSize, label: e.target.value })} placeholder="Descripción" />
            <button className="btn-ghost" onClick={addSize}>Agregar talla</button>
          </div>
        </motion.div>
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title">Colores ({selColors.length})</h3>
          <div className="var-chips">
            {colors.map((c) => (
              <button key={c.id} className={`var-chip ${selColors.includes(c.code) ? 'is-on' : ''}`}
                onClick={() => toggle(selColors, setSelColors, c.code)}>
                <span className="var-dot" style={{ background: c.hex ?? '#999' }} /> {c.label}
              </button>
            ))}
          </div>
          <div className="var-add">
            <input value={newColor.code} onChange={(e) => setNewColor({ ...newColor, code: e.target.value })} placeholder="Clave" />
            <input value={newColor.label} onChange={(e) => setNewColor({ ...newColor, label: e.target.value })} placeholder="Descripción" />
            <input type="color" value={newColor.hex} onChange={(e) => setNewColor({ ...newColor, hex: e.target.value })} title="Color" />
            <button className="btn-ghost" onClick={addColor}>Agregar color</button>
          </div>
        </motion.div>
      </div>

      <motion.div className="card var-generate" variants={fadeInUp} initial="hidden" animate="visible">
        <div>
          <strong>{combos}</strong> variante(s) se generarán
          <span className="muted"> ({selSizes.length} talla(s) × {selColors.length} color(es))</span>
        </div>
        <button className="btn-accent" disabled={busy || combos === 0} onClick={generate}>
          <Wand2 size={16} /> {busy ? 'Generando…' : 'Generar códigos para el modelo'}
        </button>
      </motion.div>
    </div>
  );
}
