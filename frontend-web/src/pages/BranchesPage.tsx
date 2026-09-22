import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import '@/pages/dashboard.css';
import './admin.css';

interface Branch {
  id: number;
  name: string;
  code: string | null;
  active: boolean;
}

/**
 * Gestión de sucursales del negocio (Dueño/Admin). Permite dar de alta sucursales, renombrarlas
 * y activarlas/desactivarlas. Cada sucursal tiene su propio inventario y sus ventas.
 */
export function BranchesPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ name: '', code: '' });

  const branches = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await api.get<Branch[]>('/branches')).data,
  });

  const createBranch = useMutation({
    mutationFn: async () =>
      api.post('/branches', { name: form.name.trim(), code: form.code.trim() || null }),
    onSuccess: () => {
      setForm({ name: '', code: '' });
      queryClient.invalidateQueries({ queryKey: ['branches'] });
    },
  });

  const toggleActive = useMutation({
    mutationFn: async (b: Branch) => api.post(`/branches/${b.id}/${b.active ? 'disable' : 'enable'}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['branches'] }),
  });

  return (
    <div>
      <h1 className="page-title">Sucursales</h1>
      <p className="page-sub">Administra las sucursales de tu negocio; cada una tiene su inventario y ventas</p>

      <div className="card">
        <h3>Agregar sucursal</h3>
        <div className="admin-grid">
          <label className="field">
            <span>Nombre</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Sucursal Centro" />
          </label>
          <label className="field">
            <span>Código (opcional)</span>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })}
              placeholder="CENTRO" />
          </label>
        </div>
        <div style={{ marginTop: 'var(--space-4)' }}>
          <button className="btn-accent" disabled={!form.name.trim() || createBranch.isPending}
            onClick={() => createBranch.mutate()}>
            {createBranch.isPending ? 'Creando…' : 'Agregar sucursal'}
          </button>
          {createBranch.isError && (
            <span className="admin-inline-error">No se pudo crear la sucursal.</span>
          )}
        </div>
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Sucursales ({branches.data?.length ?? 0})</h3>
        <table className="table">
          <thead>
            <tr><th>Sucursal</th><th>Código</th><th>Estado</th><th>Acciones</th></tr>
          </thead>
          <tbody>
            {(branches.data ?? []).map((b) => (
              <tr key={b.id}>
                <td><strong>{b.name}</strong></td>
                <td>{b.code ?? '—'}</td>
                <td>
                  <span className={`badge ${b.active ? 'badge-success' : 'badge-muted'}`}>
                    {b.active ? 'Activa' : 'Inactiva'}
                  </span>
                </td>
                <td className="admin-actions">
                  <button className={b.active ? 'btn-danger-ghost' : 'btn-ghost'}
                    onClick={() => toggleActive.mutate(b)}>
                    {b.active ? 'Desactivar' : 'Activar'}
                  </button>
                </td>
              </tr>
            ))}
            {branches.data?.length === 0 && (
              <tr><td colSpan={4} className="empty">Aún no hay sucursales.</td></tr>
            )}
          </tbody>
        </table>
        {toggleActive.isError && (
          <p className="admin-inline-error">No se pudo cambiar el estado (debe quedar al menos una activa).</p>
        )}
      </div>
    </div>
  );
}
