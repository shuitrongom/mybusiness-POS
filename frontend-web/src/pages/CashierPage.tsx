import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Calculator, Wallet, FileText, Printer } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { money } from '@/lib/format';
import { fadeInUp, pressable, staggerContainer, staggerItem } from '@/lib/motion';
import { PrintableVoucher } from '@/components/PrintableVoucher';
import { type VoucherData } from '@/components/Voucher';
import '@/pages/dashboard.css';
import '@/pages/products.css';

interface CashCut {
  id: number;
  folio: string;
  shiftId: number;
  cutType: string;
  totalSales: number;
  salesCount: number;
  expectedCash: number;
  countedCash: number | null;
  difference: number | null;
  createdBy: string | null;
  createdAt: string;
}

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
  const [voucher, setVoucher] = useState<VoucherData | null>(null);

  // Comprobante de corte (X o Z) para imprimir como respaldo.
  const cutVoucher = (c: ShiftClosure, type: 'X' | 'Z'): VoucherData => ({
    title: type === 'Z' ? 'CORTE DE CAJA Z (CIERRE)' : 'CORTE DE CAJA X (PARCIAL)',
    storeName: 'Corte de caja',
    folio: `${type}-${c.shiftId}`,
    dateTime: new Date(),
    rows: [
      { label: 'Fondo inicial', value: money(c.openingFloat) },
      { label: 'Ventas efectivo', value: money(c.cashSales) },
      { label: 'Ventas tarjeta', value: money(c.cardSales) },
      { label: 'Ventas transferencia', value: money(c.transferSales) },
      { label: 'Ventas vale', value: money(c.voucherSales) },
      { label: 'Entradas de efectivo', value: money(c.cashIn) },
      { label: 'Salidas / retiros', value: `-${money(c.cashOut)}` },
      { label: 'Efectivo esperado', value: money(c.expectedCash), strong: true },
      ...(type === 'Z' ? [
        { label: 'Efectivo contado', value: money(c.countedCash), strong: true },
        { label: 'Diferencia', value: money(c.difference), strong: true, danger: c.difference !== 0 },
      ] : []),
    ],
    signature: type === 'Z',
    footer: type === 'Z' ? 'Corte de cierre del turno · respaldo.' : 'Lectura parcial · el turno sigue abierto.',
  });

  // Reimpresión de un corte del historial (a partir de la fila persistida).
  const cutRowVoucher = (c: CashCut): VoucherData => ({
    title: c.cutType === 'Z' ? 'CORTE DE CAJA Z (CIERRE)' : 'CORTE DE CAJA X (PARCIAL)',
    storeName: 'Corte de caja',
    folio: c.folio,
    dateTime: new Date(c.createdAt),
    cashier: c.createdBy,
    rows: [
      { label: 'Ventas del turno', value: String(c.salesCount) },
      { label: 'Total vendido', value: money(c.totalSales), strong: true },
      { label: 'Efectivo esperado', value: money(c.expectedCash), strong: true },
      ...(c.countedCash != null ? [{ label: 'Efectivo contado', value: money(c.countedCash), strong: true }] : []),
      ...(c.difference != null ? [{ label: 'Diferencia', value: money(c.difference), strong: true, danger: c.difference !== 0 }] : []),
    ],
    signature: c.cutType === 'Z',
    footer: 'Reimpresión de corte · respaldo.',
  });

  const branches = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await api.get<Branch[]>('/branches')).data,
  });
  const registers = useQuery({
    queryKey: ['cash-registers'],
    queryFn: async () => (await api.get<CashRegister[]>('/cash-registers')).data,
  });
  const queryClient = useQueryClient();
  const cuts = useQuery({
    queryKey: ['cash-cuts'],
    queryFn: async () => (await api.get<CashCut[]>('/shifts/cuts', { params: { limit: 15 } })).data,
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
      toast.success('Turno abierto', `Turno #${data.shiftId} listo para operar.`);
    },
    onError: () => {
      toast.error('No se pudo abrir el turno', 'Revisa la caja seleccionada e inténtalo de nuevo.');
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
      // Comprobante del movimiento (entrada/salida de efectivo) para respaldo.
      setVoucher({
        title: movement.direction === 'IN' ? 'ENTRADA DE EFECTIVO' : 'SALIDA / RETIRO DE EFECTIVO',
        storeName: 'Movimiento de caja',
        folio: `MOV-${openedShiftId}`,
        dateTime: new Date(),
        rows: [
          { label: 'Tipo', value: movement.direction === 'IN' ? 'Entrada' : 'Salida' },
          { label: 'Importe', value: money(Number(movement.amount) || 0), strong: true },
        ],
        note: movement.reason ? `Motivo: ${movement.reason}` : null,
        signature: true,
        footer: 'Comprobante de movimiento de efectivo.',
      });
      setMovement((m) => ({ ...m, amount: '', reason: '' }));
      toast.success('Movimiento registrado', 'Se actualizó el efectivo del turno y se imprimió el comprobante.');
    },
    onError: () => {
      toast.error('No se pudo registrar el movimiento', 'Revisa los datos e inténtalo de nuevo.');
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
      setVoucher(cutVoucher(data, 'Z'));
      queryClient.invalidateQueries({ queryKey: ['cash-cuts'] });
      toast.success('Turno cerrado', `Diferencia del arqueo: ${money(data.difference)}.`);
    },
    onError: () => {
      toast.error('No se pudo cerrar el turno', 'Revisa el efectivo contado e inténtalo de nuevo.');
    },
  });

  const cutX = useMutation({
    mutationFn: async () =>
      (await api.post<ShiftClosure>(`/shifts/${openedShiftId}/cut-x`, {})).data,
    onSuccess: (data) => {
      setClosure(data);
      setVoucher(cutVoucher(data, 'X'));
      queryClient.invalidateQueries({ queryKey: ['cash-cuts'] });
      toast.success('Corte X generado', 'Lectura parcial del turno (no cierra la caja).');
    },
    onError: () => toast.error('No se pudo generar el corte X'),
  });

  const activeBranches = (branches.data ?? []).filter((b) => b.active);

  return (
    <div>
      <h1 className="page-title">Cortes de caja</h1>
      <p className="page-sub">Elige sucursal y caja, abre turno, registra efectivo y haz el arqueo</p>

      {openedShiftId === null ? (
        <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Wallet size={18} /> Abrir turno</h3>
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
            <motion.button className="btn-accent" disabled={!cashRegisterId || openShift.isPending}
              onClick={() => openShift.mutate()}
              whileHover={!cashRegisterId || openShift.isPending ? undefined : pressable.whileHover}
              whileTap={!cashRegisterId || openShift.isPending ? undefined : pressable.whileTap}
              transition={pressable.transition}>
              {openShift.isPending ? 'Abriendo…' : 'Abrir turno'}
            </motion.button>
          </div>
          {branchRegisters.length === 0 && branchId !== 0 && (
            <p className="admin-plan-hint" style={{ marginTop: 'var(--space-3)' }}>
              Esta sucursal no tiene cajas. Créalas en la sección de sucursales/cajas.
            </p>
          )}
        </motion.div>
      ) : (
        <>
          <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
            <p style={{ margin: 0 }}>
              <span className="badge badge-success">Turno abierto</span>{' '}
              Turno <strong>#{openedShiftId}</strong> en la caja seleccionada.
            </p>
          </motion.div>

          <motion.div className="card" style={{ marginTop: 'var(--space-5)' }} variants={fadeInUp} initial="hidden" animate="visible">
            <h3 className="sec-title"><Wallet size={18} /> Movimiento de efectivo</h3>
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
              <motion.button className="btn-primary" disabled={!movement.amount || recordMovement.isPending}
                onClick={() => { setMovementMsg(null); recordMovement.mutate(); }}
                whileHover={!movement.amount || recordMovement.isPending ? undefined : pressable.whileHover}
                whileTap={!movement.amount || recordMovement.isPending ? undefined : pressable.whileTap}
                transition={pressable.transition}>Registrar</motion.button>
            </div>
            {movementMsg && <p style={{ marginTop: 'var(--space-3)' }}><span className="badge badge-success">Listo</span> {movementMsg}</p>}
          </motion.div>

          <motion.div className="card" style={{ marginTop: 'var(--space-5)' }} variants={fadeInUp} initial="hidden" animate="visible">
            <div className="sec-title-row">
              <h3 className="sec-title"><Calculator size={18} /> Cerrar turno (arqueo)</h3>
              <motion.button className="btn-ghost" disabled={cutX.isPending} onClick={() => cutX.mutate()}
                whileHover={cutX.isPending ? undefined : pressable.whileHover}
                whileTap={cutX.isPending ? undefined : pressable.whileTap}
                transition={pressable.transition}>
                <FileText size={15} /> {cutX.isPending ? 'Generando…' : 'Corte X (parcial)'}
              </motion.button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end', maxWidth: 460 }}>
              <label className="field"><span>Efectivo contado</span>
                <input type="number" step="0.01" value={countedCash}
                  onChange={(e) => setCountedCash(e.target.value)} placeholder="0.00" /></label>
              <motion.button className="btn-accent" disabled={closeShift.isPending} onClick={() => closeShift.mutate()}
                whileHover={closeShift.isPending ? undefined : pressable.whileHover}
                whileTap={closeShift.isPending ? undefined : pressable.whileTap}
                transition={pressable.transition}>
                {closeShift.isPending ? 'Cerrando…' : 'Corte Z (cerrar)'}
              </motion.button>
            </div>
          </motion.div>
        </>
      )}

      {closure && (
        <motion.div className="card" style={{ marginTop: 'var(--space-5)' }} variants={fadeInUp} initial="hidden" animate="visible">
          <h3 className="sec-title"><Calculator size={18} /> Corte del turno #{closure.shiftId}</h3>
          <table className="table" style={{ marginTop: 'var(--space-3)' }}>
            <motion.tbody variants={staggerContainer} initial="hidden" animate="visible">
              <motion.tr variants={staggerItem}><td>Fondo inicial</td><td>{money(closure.openingFloat)}</td></motion.tr>
              <motion.tr variants={staggerItem}><td>Ventas en efectivo</td><td>{money(closure.cashSales)}</td></motion.tr>
              <motion.tr variants={staggerItem}><td>Ventas con tarjeta</td><td>{money(closure.cardSales)}</td></motion.tr>
              <motion.tr variants={staggerItem}><td>Ventas por transferencia</td><td>{money(closure.transferSales)}</td></motion.tr>
              <motion.tr variants={staggerItem}><td>Entradas / Salidas de efectivo</td><td>{money(closure.cashIn)} / {money(closure.cashOut)}</td></motion.tr>
              <motion.tr variants={staggerItem}><td><strong>Efectivo esperado</strong></td><td><strong>{money(closure.expectedCash)}</strong></td></motion.tr>
              <motion.tr variants={staggerItem}><td>Efectivo contado</td><td>{money(closure.countedCash)}</td></motion.tr>
              <motion.tr variants={staggerItem}>
                <td><strong>Diferencia</strong></td>
                <td>
                  <span className={`badge ${closure.difference === 0 ? 'badge-success' : 'badge-danger'}`}>
                    {money(closure.difference)}
                  </span>
                </td>
              </motion.tr>
            </motion.tbody>
          </table>
        </motion.div>
      )}

      <motion.div className="card" style={{ marginTop: 'var(--space-5)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><FileText size={18} /> Historial de cortes (X / Z)</h3>
        <table className="table" style={{ marginTop: 'var(--space-3)' }}>
          <thead>
            <tr>
              <th>Folio</th><th>Tipo</th><th>Turno</th>
              <th style={{ textAlign: 'right' }}>Ventas</th>
              <th style={{ textAlign: 'right' }}>Total</th>
              <th style={{ textAlign: 'right' }}>Diferencia</th>
              <th>Fecha</th><th></th>
            </tr>
          </thead>
          <tbody>
            {(cuts.data ?? []).map((c) => (
              <tr key={c.id}>
                <td><strong>{c.folio}</strong></td>
                <td>
                  <span className={`badge ${c.cutType === 'Z' ? 'badge-danger' : 'badge-muted'}`}>
                    Corte {c.cutType}
                  </span>
                </td>
                <td>#{c.shiftId}</td>
                <td style={{ textAlign: 'right' }}>{c.salesCount}</td>
                <td style={{ textAlign: 'right' }}>{money(c.totalSales)}</td>
                <td style={{ textAlign: 'right' }}>{c.difference != null ? money(c.difference) : '—'}</td>
                <td>{new Date(c.createdAt).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' })}</td>
                <td style={{ textAlign: 'right' }}>
                  <button className="icon-btn" onClick={() => setVoucher(cutRowVoucher(c))} aria-label="Imprimir">
                    <Printer size={15} />
                  </button>
                </td>
              </tr>
            ))}
            {(cuts.data ?? []).length === 0 && (
              <tr><td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: 'var(--space-5)' }}>
                Aún no hay cortes registrados.
              </td></tr>
            )}
          </tbody>
        </table>
      </motion.div>

      {voucher && <PrintableVoucher data={voucher} onDone={() => setVoucher(null)} />}
    </div>
  );
}
