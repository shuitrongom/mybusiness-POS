import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { money } from '@/lib/format';
import '@/pages/dashboard.css';

interface Branch {
  id: number;
  name: string;
  active: boolean;
}

interface CashRegister {
  id: number;
  name: string;
  active: boolean;
  branchId: number;
  branchName: string;
}

interface ShiftClosure {
  shiftId: number;
  openingFloat: number;
  cashSales: number;
  cardSales: number;
  transferSales: number;
  voucherSales: number;
  cashIn: number;
  cashOut: number;
  expectedCash: number;
  countedCash: number;
  difference: number;
}

/**
 * Cortes de caja / turnos: se elige la sucursal y la caja, se abre turno con fondo, se registran
 * entradas/salidas de efectivo y se cierra con arqueo, mostrando el corte con la diferencia.
 */
export function CashierPage() {
  const [branchId, setBranchId] = useState<number>(0);
  const [cashRegisterId, setCashRegisterId] = useState<number>(0);
  const [openingFloat, setOpeningFloat] = useState('');
  const [openedShiftId, setOpenedShiftId] = useState<number | null>(null);

  const [movement, setMovement] = useState({ direction: 'OUT', amount: '', reason: '' });
  const [movementMsg, setMovementMsg] = useState<string | null>(null);

  const [countedCash, setCountedCash] = useState('');
  const [closure, setClosure] = useState<ShiftClosure | null>(null);

  const branches = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await api.get<Branch[]>('/branches')).data,
  });
  const registers = useQuery({
    queryKey: ['cash-registers'],
    queryFn: async () => (await api.get<CashRegister[]>('/cash-registers')).data,
  });

  // Elige la primera sucursal activa por defecto.
  useEffect(() => {
    const active = (branches.data ?? []).filter((b) => b.active);
    if (active.length > 0 && branchId === 0) setBranchId(active[0].id);
  }, [branches.data, branchId]);

  // Cajas de la sucursal elegida.
  const branchRegisters = useMemo(
    () => (registers.data ?? []).filter((r) => r.branchId === branchId && r.active),
    [registers.data, branchId],
  );
  useEffect(() => {
    if (branchRegisters.length > 0 && !branchRegisters.some((r) => r.id === cashRegisterId)) {
      setCashRegisterId(branchRegisters[0].id);
    }
  }, [branchRegisters, cashRegisterId]);

  const openShift = useMutation({
    mutationFn: async () =>
      (await api.post<{ shiftId: number }>('/shifts/open', {
        cashRegisterId,
        openingFloat: Number(openingFloat) || 0,
      })).data,
    onSuccess: (data) => {
      setOpenedShiftId(data.shiftId);
      setClosure(null);
    },
  });

  const recordMovement = useMutation({
    mutationFn: async () =>
      api.post(`/shifts/${openedShiftId}/cash-movement`, {
        direction: movement.direction,
        amount: Number(movement.amount),
        reason: movement.reason,
      }),
    onSuccess: () => {
      setMovementMsg('Movimiento de efectivo registrado.');
      setMovement((m) => ({ ...m, amount: '', reason: '' }));
    },
  });

  const closeShift = useMutation({
    mutationFn: async () =>
      (await api.post<ShiftClosure>(`/shifts/${openedShiftId}/close`, {
        countedCash: Number(countedCash),
      })).data,
    onSuccess: (data) => {
      setClosure(data);
      setOpenedShiftId(null);
      setCountedCash('');
    },
  });

  const activeBranches = (branches.data ?? []).filter((b) => b.active);

  return (
    <div>
      <h1 className="page-title">Cortes de caja</h1>
      <p className="page-sub">Elige sucursal y caja, abre turno, registra efectivo y haz el arqueo</p>

      {openedShiftId === null ? (
        <div className="card">
          <h3>Abrir turno</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
            <label className="field"><span>Sucursal</span>
              <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
                {activeBranches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            </label>
            <label className="field"><span>Caja</span>
              <select value={cashRegisterId} onChange={(e) => setCashRegisterId(Number(e.target.value))}
                disabled={branchRegisters.length === 0}>
                {branchRegisters.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
              </select>
            </label>
            <label className="field"><span>Fondo inicial</span>
              <input type="number" step="0.01" value={openingFloat}
                onChange={(e) => setOpeningFloat(e.target.value)} placeholder="0.00" />
            </label>
            <button className="btn-accent" disabled={!cashRegisterId || openShift.isPending}
              onClick={() => openShift.mutate()}>
              {openShift.isPending ? 'Abriendo…' : 'Abrir turno'}
            </button>
          </div>
          {branchRegisters.length === 0 && branchId !== 0 && (
            <p className="admin-plan-hint" style={{ marginTop: 'var(--space-3)' }}>
              Esta sucursal no tiene cajas. Créalas en la sección de sucursales/cajas.
            </p>
          )}
        </div>
      ) : (
        <>
          <div className="card">
            <p style={{ margin: 0 }}>
              <span className="badge badge-success">Turno abierto</span>{' '}
              Turno <strong>#{openedShiftId}</strong> en la caja seleccionada.
            </p>
          </div>

          <div className="card" style={{ marginTop: 'var(--space-5)' }}>
            <h3>Movimiento de efectivo</h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 2fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
              <label className="field"><span>Tipo</span>
                <select value={movement.direction} onChange={(e) => setMovement({ ...movement, direction: e.target.value })}>
                  <option value="OUT">Salida (retiro/gasto)</option>
                  <option value="IN">Entrada (ingreso)</option>
                </select></label>
              <label className="field"><span>Monto</span>
                <input type="number" step="0.01" value={movement.amount}
                  onChange={(e) => setMovement({ ...movement, amount: e.target.value })} /></label>
              <label className="field"><span>Motivo</span>
                <input value={movement.reason}
                  onChange={(e) => setMovement({ ...movement, reason: e.target.value })} /></label>
              <button className="btn-primary" disabled={!movement.amount || recordMovement.isPending}
                onClick={() => { setMovementMsg(null); recordMovement.mutate(); }}>Registrar</button>
            </div>
            {movementMsg && <p style={{ marginTop: 'var(--space-3)' }}><span className="badge badge-success">Listo</span> {movementMsg}</p>}
          </div>

          <div className="card" style={{ marginTop: 'var(--space-5)' }}>
            <h3>Cerrar turno (arqueo)</h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end', maxWidth: 460 }}>
              <label className="field"><span>Efectivo contado</span>
                <input type="number" step="0.01" value={countedCash}
                  onChange={(e) => setCountedCash(e.target.value)} placeholder="0.00" /></label>
              <button className="btn-accent" disabled={closeShift.isPending} onClick={() => closeShift.mutate()}>
                {closeShift.isPending ? 'Cerrando…' : 'Cerrar turno'}
              </button>
            </div>
          </div>
        </>
      )}

      {closure && (
        <div className="card" style={{ marginTop: 'var(--space-5)' }}>
          <h3>Corte del turno #{closure.shiftId}</h3>
          <table className="table" style={{ marginTop: 'var(--space-3)' }}>
            <tbody>
              <tr><td>Fondo inicial</td><td>{money(closure.openingFloat)}</td></tr>
              <tr><td>Ventas en efectivo</td><td>{money(closure.cashSales)}</td></tr>
              <tr><td>Ventas con tarjeta</td><td>{money(closure.cardSales)}</td></tr>
              <tr><td>Ventas por transferencia</td><td>{money(closure.transferSales)}</td></tr>
              <tr><td>Entradas / Salidas de efectivo</td><td>{money(closure.cashIn)} / {money(closure.cashOut)}</td></tr>
              <tr><td><strong>Efectivo esperado</strong></td><td><strong>{money(closure.expectedCash)}</strong></td></tr>
              <tr><td>Efectivo contado</td><td>{money(closure.countedCash)}</td></tr>
              <tr>
                <td><strong>Diferencia</strong></td>
                <td>
                  <span className={`badge ${closure.difference === 0 ? 'badge-success' : 'badge-danger'}`}>
                    {money(closure.difference)}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
