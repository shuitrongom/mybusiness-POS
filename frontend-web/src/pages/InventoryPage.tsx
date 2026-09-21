import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import '@/pages/dashboard.css';

interface StockLevel {
  productId: number;
  branchId: number;
  quantity: number;
  minQuantity: number;
}

/**
 * Pantalla de inventario: consulta de existencia y alertas de stock mínimo, ajuste manual y
 * configuración de mínimos. Consume los endpoints del módulo de inventario.
 */
export function InventoryPage() {
  const queryClient = useQueryClient();
  const [adjust, setAdjust] = useState({ productId: '', branchId: '1', delta: '', reason: '' });

  const alerts = useQuery({
    queryKey: ['inventory', 'low-stock'],
    queryFn: async () => (await api.get<StockLevel[]>('/inventory/alerts/low-stock')).data,
  });

  const doAdjust = useMutation({
    mutationFn: async () =>
      api.post('/inventory/adjust', {
        productId: Number(adjust.productId),
        branchId: Number(adjust.branchId),
        delta: Number(adjust.delta),
        reason: adjust.reason,
      }),
    onSuccess: () => {
      setAdjust({ productId: '', branchId: '1', delta: '', reason: '' });
      queryClient.invalidateQueries({ queryKey: ['inventory'] });
    },
  });

  return (
    <div>
      <h1 className="page-title">Inventario</h1>
      <p className="page-sub">Existencias, ajustes y alertas</p>

      <div className="card">
        <h3>Ajuste de inventario</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 2fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Producto (id)</span>
            <input value={adjust.productId} onChange={(e) => setAdjust({ ...adjust, productId: e.target.value })} /></label>
          <label className="field"><span>Sucursal (id)</span>
            <input value={adjust.branchId} onChange={(e) => setAdjust({ ...adjust, branchId: e.target.value })} /></label>
          <label className="field"><span>Cantidad (+/-)</span>
            <input type="number" value={adjust.delta} onChange={(e) => setAdjust({ ...adjust, delta: e.target.value })} /></label>
          <label className="field"><span>Motivo</span>
            <input value={adjust.reason} onChange={(e) => setAdjust({ ...adjust, reason: e.target.value })} /></label>
          <button className="btn-accent" disabled={!adjust.productId || !adjust.delta || doAdjust.isPending}
                  onClick={() => doAdjust.mutate()}>Aplicar</button>
        </div>
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Alertas de stock mínimo</h3>
        <table className="table">
          <thead><tr><th>Producto</th><th>Sucursal</th><th>Existencia</th><th>Mínimo</th></tr></thead>
          <tbody>
            {(alerts.data ?? []).map((s) => (
              <tr key={`${s.productId}-${s.branchId}`}>
                <td>{s.productId}</td><td>{s.branchId}</td>
                <td><span className="badge badge-danger">{s.quantity}</span></td>
                <td>{s.minQuantity}</td>
              </tr>
            ))}
            {alerts.data?.length === 0 && (
              <tr><td colSpan={4} className="empty">Sin alertas: todo el inventario está por encima del mínimo.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
