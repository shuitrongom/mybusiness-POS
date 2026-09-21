import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { money } from '@/lib/format';
import '@/pages/dashboard.css';

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
 * Cortes de caja / turnos: abrir turno con fondo, registrar entradas/salidas de efectivo y
 * cerrar con arqueo, mostrando el corte con la diferencia entre lo esperado y lo contado.
 */
export function CashierPage() {
  const [open, setOpen] = useState({ cashRegisterId: '1', openingFloat: '' });
  const [openedShiftId, setOpenedShiftId] = useState<number | null>(null);

  const [movement, setMovement] = useState({ shiftId: '', direction: 'OUT', amount: '', reason: '' });
  const [movementMsg, setMovementMsg] = useState<string | null>(null);

  const [close, setClose] = useState({ shiftId: '', countedCash: '' });
  const [closure, setClosure] = useState<ShiftClosure | null>(null);

  const openShift = useMutation({
    mutationFn: async () =>
      (await api.post<{ shiftId: number }>('/shifts/open', {
        cashRegisterId: Number(open.cashRegisterId),
        openingFloat: Number(open.openingFloat) || 0,
      })).data,
    onSuccess: (data) => {
      setOpenedShiftId(data.shiftId);
      setMovement((m) => ({ ...m, shiftId: String(data.shiftId) }));
      setClose((c) => ({ ...c, shiftId: String(data.shiftId) }));
    },
  });

  const recordMovement = useMutation({
    mutationFn: async () =>
      api.post(`/shifts/${Number(movement.shiftId)}/cash-movement`, {
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
      (await api.post<ShiftClosure>(`/shifts/${Number(close.shiftId)}/close`, {
        countedCash: Number(close.countedCash),
      })).data,
    onSuccess: (data) => setClosure(data),
  });

  return (
    <div>
      <h1 className="page-title">Cortes de caja</h1>
      <p className="page-sub">Turnos, movimientos de efectivo y arqueo</p>

      <div className="card">
        <h3>Abrir turno</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end', maxWidth: 560 }}>
          <label className="field"><span>Caja (id)</span>
            <input value={open.cashRegisterId} onChange={(e) => setOpen({ ...open, cashRegisterId: e.target.value })} /></label>
          <label className="field"><span>Fondo inicial</span>
            <input type="number" step="0.01" value={open.openingFloat} onChange={(e) => setOpen({ ...open, openingFloat: e.target.value })} /></label>
          <button className="btn-accent" disabled={openShift.isPending} onClick={() => openShift.mutate()}>Abrir</button>
        </div>
        {openedShiftId !== null && (
          <p style={{ marginTop: 'var(--space-3)' }}>
            <span className="badge badge-success">Turno abierto</span> Id de turno: <strong>{openedShiftId}</strong>
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Movimiento de efectivo</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 2fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Turno (id)</span>
            <input value={movement.shiftId} onChange={(e) => setMovement({ ...movement, shiftId: e.target.value })} /></label>
          <label className="field"><span>Tipo</span>
            <select value={movement.direction} onChange={(e) => setMovement({ ...movement, direction: e.target.value })}>
              <option value="OUT">Salida (retiro/gasto)</option>
              <option value="IN">Entrada (ingreso)</option>
            </select></label>
          <label className="field"><span>Monto</span>
            <input type="number" step="0.01" value={movement.amount} onChange={(e) => setMovement({ ...movement, amount: e.target.value })} /></label>
          <label className="field"><span>Motivo</span>
            <input value={movement.reason} onChange={(e) => setMovement({ ...movement, reason: e.target.value })} /></label>
          <button className="btn-primary" disabled={!movement.shiftId || !movement.amount || recordMovement.isPending}
                  onClick={() => { setMovementMsg(null); recordMovement.mutate(); }}>Registrar</button>
        </div>
        {movementMsg && <p style={{ marginTop: 'var(--space-3)' }}><span className="badge badge-success">Listo</span> {movementMsg}</p>}
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Cerrar turno (arqueo)</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end', maxWidth: 560 }}>
          <label className="field"><span>Turno (id)</span>
            <input value={close.shiftId} onChange={(e) => setClose({ ...close, shiftId: e.target.value })} /></label>
          <label className="field"><span>Efectivo contado</span>
            <input type="number" step="0.01" value={close.countedCash} onChange={(e) => setClose({ ...close, countedCash: e.target.value })} /></label>
          <button className="btn-accent" disabled={!close.shiftId || closeShift.isPending} onClick={() => closeShift.mutate()}>Cerrar</button>
        </div>

        {closure && (
          <table className="table" style={{ marginTop: 'var(--space-4)' }}>
            <tbody>
              <tr><td>Fondo inicial</td><td>{money(closure.openingFloat)}</td></tr>
              <tr><td>Ventas en efectivo</td><td>{money(closure.cashSales)}</td></tr>
              <tr><td>Ventas con tarjeta</td><td>{money(closure.cardSales)}</td></tr>
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
        )}
      </div>
    </div>
  );
}
