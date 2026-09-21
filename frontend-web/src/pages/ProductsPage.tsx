import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import '@/pages/dashboard.css';

interface Product {
  id: number;
  name: string;
  unit: string;
  price: number;
  barcodes: string[];
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/**
 * Catálogo de productos del negocio: listar, buscar y dar de alta (con código de barras).
 */
export function ProductsPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [form, setForm] = useState({ name: '', price: '', barcode: '', unit: 'pieza' });

  const products = useQuery({
    queryKey: ['products', query],
    queryFn: async () =>
      (await api.get<Product[]>(`/catalog/products/search?q=${encodeURIComponent(query || '')}`)).data,
    enabled: query.length > 0,
  });

  const create = useMutation({
    mutationFn: async () =>
      api.post('/catalog/products', {
        name: form.name,
        unit: form.unit,
        price: Number(form.price),
        barcodes: form.barcode ? [form.barcode] : [],
      }),
    onSuccess: () => {
      setForm({ name: '', price: '', barcode: '', unit: 'pieza' });
      queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });

  return (
    <div>
      <h1 className="page-title">Productos</h1>
      <p className="page-sub">Catálogo de tu negocio</p>

      <div className="card">
        <h3>Nuevo producto</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field">
            <span>Nombre</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </label>
          <label className="field">
            <span>Precio</span>
            <input type="number" step="0.01" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />
          </label>
          <label className="field">
            <span>Código de barras</span>
            <input value={form.barcode} onChange={(e) => setForm({ ...form, barcode: e.target.value })} />
          </label>
          <button className="btn-accent" disabled={!form.name || create.isPending} onClick={() => create.mutate()}>
            {create.isPending ? 'Guardando…' : 'Agregar'}
          </button>
        </div>
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <label className="field" style={{ maxWidth: 360 }}>
          <span>Buscar</span>
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Nombre del producto…" />
        </label>

        <table className="table">
          <thead>
            <tr><th>Producto</th><th>Unidad</th><th>Precio</th><th>Código</th></tr>
          </thead>
          <tbody>
            {(products.data ?? []).map((p) => (
              <tr key={p.id}>
                <td>{p.name}</td>
                <td>{p.unit}</td>
                <td>{money(p.price)}</td>
                <td>{p.barcodes?.[0] ?? '—'}</td>
              </tr>
            ))}
            {query.length > 0 && products.data?.length === 0 && (
              <tr><td colSpan={4} className="empty">Sin resultados.</td></tr>
            )}
            {query.length === 0 && (
              <tr><td colSpan={4} className="empty">Escribe para buscar productos.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
