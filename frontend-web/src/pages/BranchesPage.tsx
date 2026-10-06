import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Building2, Store } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp, pressable, staggerContainer, staggerItem } from '@/lib/motion';
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
      toast.success('Sucursal creada', 'Ya puedes operar con ella.');
    },
    onError: () => {
      toast.error('No se pudo crear la sucursal', 'Revisa los datos e inténtalo de nuevo.');
    },
  });

  const toggleActive = useMutation({
    mutationFn: async (b: Branch) => api.post(`/branches/${b.id}/${b.active ? 'disable' : 'enable'}`),
    onSuccess: (_data, b) => {
      queryClient.invalidateQueries({ queryKey: ['branches'] });
      toast.success(b.active ? 'Sucursal desactivada' : 'Sucursal activada');
    },
    onError: () => {
      toast.error('No se pudo cambiar el estado', 'Debe quedar al menos una sucursal activa.');
    },
  });

  return (
    <div>
      <h1 className="page-title">Sucursales</h1>
      <p className="page-sub">Administra las sucursales de tu negocio; cada una tiene su inventario y ventas</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Building2 size={18} /> Agregar sucursal</h3>
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
          <motion.button className="btn-accent" disabled={!form.name.trim() || createBranch.isPending}
            onClick={() => createBranch.mutate()}
            whileHover={!form.name.trim() || createBranch.isPending ? undefined : pressable.whileHover}
            whileTap={!form.name.trim() || createBranch.isPending ? undefined : pressable.whileTap}
            transition={pressable.transition}>
            {createBranch.isPending ? 'Creando…' : 'Agregar sucursal'}
          </motion.button>
          {createBranch.isError && (
            <span className="admin-inline-error">No se pudo crear la sucursal.</span>
          )}
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-5)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Store size={18} /> Sucursales ({branches.data?.length ?? 0})</h3>
        <table className="table">
          <thead>
            <tr><th>Sucursal</th><th>Código</th><th>Estado</th><th>Acciones</th></tr>
          </thead>
          <motion.tbody variants={staggerContainer} initial="hidden" animate="visible">
            {(branches.data ?? []).map((b) => (
              <motion.tr key={b.id} variants={staggerItem}>
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
              </motion.tr>
            ))}
            {branches.data?.length === 0 && (
              <tr><td colSpan={4} className="empty">Aún no hay sucursales.</td></tr>
            )}
          </motion.tbody>
        </table>
        {toggleActive.isError && (
          <p className="admin-inline-error">No se pudo cambiar el estado (debe quedar al menos una activa).</p>
        )}
      </motion.div>
    </div>
  );
}
