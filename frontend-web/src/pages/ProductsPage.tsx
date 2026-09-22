import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { fileToThumbnailDataUrl } from '@/lib/image';
import '@/pages/dashboard.css';
import './products.css';

interface Product {
  id: number;
  name: string;
  unit: string;
  price: number;
  barcodes: string[];
  imageUrl: string | null;
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

/**
 * Catálogo de productos del negocio: listar (con foto), buscar y dar de alta. La foto se captura
 * desde el dispositivo, se reduce a una miniatura y se guarda junto al producto.
 */
export function ProductsPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [form, setForm] = useState({ name: '', price: '', barcode: '', unit: 'pieza', imageUrl: '' });
  const [imgError, setImgError] = useState<string | null>(null);

  const products = useQuery({
    queryKey: ['products', 'all'],
    queryFn: async () => (await api.get<Product[]>('/catalog/products')).data,
  });

  const create = useMutation({
    mutationFn: async () =>
      api.post('/catalog/products', {
        name: form.name.trim(),
        unit: form.unit,
        price: Number(form.price) || 0,
        barcodes: form.barcode ? [form.barcode] : [],
        imageUrl: form.imageUrl || null,
      }),
    onSuccess: () => {
      setForm({ name: '', price: '', barcode: '', unit: 'pieza', imageUrl: '' });
      queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });

  const onPickImage = async (e: React.ChangeEvent<HTMLInputElement>) => {
    setImgError(null);
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const dataUrl = await fileToThumbnailDataUrl(file);
      setForm((f) => ({ ...f, imageUrl: dataUrl }));
    } catch {
      setImgError('No se pudo procesar la imagen. Prueba con otra.');
    }
  };

  const filtered = (products.data ?? []).filter(
    (p) => query.trim() === '' || p.name.toLowerCase().includes(query.trim().toLowerCase()),
  );

  return (
    <div>
      <h1 className="page-title">Productos</h1>
      <p className="page-sub">Catálogo de tu negocio con fotos</p>

      <div className="card">
        <h3>Nuevo producto</h3>
        <div className="prod-form">
          <div className="prod-photo">
            <label className="prod-photo-drop">
              {form.imageUrl ? (
                <img src={form.imageUrl} alt="Vista previa" className="prod-photo-preview" />
              ) : (
                <span className="prod-photo-placeholder">📷<br />Agregar foto</span>
              )}
              <input type="file" accept="image/*" onChange={onPickImage} hidden />
            </label>
            {form.imageUrl && (
              <button className="prod-photo-clear" onClick={() => setForm({ ...form, imageUrl: '' })}>
                Quitar foto
              </button>
            )}
          </div>

          <div className="prod-fields">
            <label className="field">
              <span>Nombre</span>
              <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="Coca-Cola 600 ml" />
            </label>
            <div className="prod-fields-row">
              <label className="field">
                <span>Precio</span>
                <input type="number" step="0.01" value={form.price}
                  onChange={(e) => setForm({ ...form, price: e.target.value })} placeholder="0.00" />
              </label>
              <label className="field">
                <span>Unidad</span>
                <select value={form.unit} onChange={(e) => setForm({ ...form, unit: e.target.value })}>
                  <option value="pieza">Pieza</option>
                  <option value="kg">Kilogramo</option>
                  <option value="litro">Litro</option>
                  <option value="paquete">Paquete</option>
                </select>
              </label>
              <label className="field">
                <span>Código de barras</span>
                <input value={form.barcode} onChange={(e) => setForm({ ...form, barcode: e.target.value })}
                  placeholder="750..." />
              </label>
            </div>
            {imgError && <small className="field-error">{imgError}</small>}
            <div>
              <button className="btn-accent" disabled={!form.name.trim() || create.isPending}
                onClick={() => create.mutate()}>
                {create.isPending ? 'Guardando…' : 'Agregar producto'}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <div className="prod-list-head">
          <h3>Productos ({filtered.length})</h3>
          <input className="prod-search" value={query} onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar producto…" />
        </div>
        <table className="table">
          <thead>
            <tr><th></th><th>Producto</th><th>Unidad</th><th>Precio</th><th>Código</th></tr>
          </thead>
          <tbody>
            {filtered.map((p) => (
              <tr key={p.id}>
                <td>
                  {p.imageUrl ? (
                    <img src={p.imageUrl} alt={p.name} className="prod-thumb" />
                  ) : (
                    <span className="prod-thumb prod-thumb-avatar" style={{ background: colorFor(p.name) }}>
                      {initials(p.name)}
                    </span>
                  )}
                </td>
                <td><strong>{p.name}</strong></td>
                <td>{p.unit}</td>
                <td>{money(p.price)}</td>
                <td>{p.barcodes?.[0] ?? '—'}</td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr><td colSpan={5} className="empty">
                {products.data?.length === 0 ? 'Aún no hay productos.' : 'Sin resultados.'}
              </td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
