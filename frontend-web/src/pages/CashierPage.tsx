import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Calculator, Wallet, FileText, Printer, RotateCcw, Receipt, Sunrise, Moon } from 'lucide-react';
import { api, closeOptions, type CloseOptions } from '@/lib/api';
import { toast } from '@/store/toast';
import { money } from '@/lib/format';
import { fadeInUp, pressable, staggerContainer, staggerItem } from '@/lib/motion';
import { PrintableVoucher } from '@/components/PrintableVoucher';
import { type VoucherData } from '@/components/Voucher';
import { ScopeSelector, type ReportScope } from '@/components/ScopeSelector';
import { InfoBanner } from '@/components/InfoBanner';
import { ConfirmFinDiaModal } from '@/components/ConfirmFinDiaModal';
import { ReopenShiftModal } from '@/components/ReopenShiftModal';
import { useTestMode } from '@/lib/testMode';
import { useSession } from '@/store/session';
import { decodeToken, isOwner } from '@/lib/jwt';
import '@/pages/dashboard.css';
import '@/pages/products.css';
import '@/pages/cash-cuts.css';

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
  isTest: boolean;
  voided: boolean;
  voidedAt: string | null;
  voidedBy: string | null;
  voidedReason: string | null;
  shiftStatus: string;
  shiftBusinessDate: string;
  reopenable: boolean;
  hadFinDiaWaste: boolean;
}

/** Resultado de reabrir un turno (POST /shifts/{id}/reopen). */
interface ReopenResult {
  shiftId: number;
  alreadyOpen: boolean;
  voidedCutId: number | null;
  voidedCutFolio: string | null;
  voidedCountedCash: number | null;
  salesCount: number;
  hadFinDiaWaste: boolean;
  isTest: boolean | null;
  reopenedBy: string | null;
  reopenedAt: string | null;
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

/** Modo de cierre de caja: relevo (no manda sobrante a merma) o fin de jornada (sí). */
type CloseMode = 'TURNO' | 'FIN_DIA';

/** Fallback conservador de opciones de cierre (§7.2): ante cualquier error ≠ 200. */
const CLOSE_OPTS_FALLBACK: CloseOptions = {
  shiftId: 0,
  isFirstOfDay: true,
  onlyShiftSoFar: false,
  totalShiftsToday: 1,
  suggestedMode: 'TURNO',
  allowFinDia: true,
  requireStrongConfirm: true,
  otherOpenInBranch: false,
};

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

  // Cierre inteligente (Opción B, H4): modo elegido + señal del backend + confirmación fuerte.
  const [closeMode, setCloseMode] = useState<CloseMode>('TURNO');
  const [opts, setOpts] = useState<CloseOptions | null>(null);
  const [confirmFinDia, setConfirmFinDia] = useState(false);

  // Reapertura de turno (solo el dueño). El objetivo guarda el corte Z a reabrir.
  const token = useSession((s) => s.token);
  const owner = isOwner(decodeToken(token));
  const [reopenTarget, setReopenTarget] = useState<CashCut | null>(null);

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
  // Selector de scope (modo prueba): default 'both' si el modo prueba está ON, 'real' si OFF.
  const { active: testModeActive } = useTestMode();
  const [scopePick, setScopePick] = useState<ReportScope | null>(null);
  const scope: ReportScope = scopePick ?? (testModeActive ? 'both' : 'real');
  const cuts = useQuery({
    queryKey: ['cash-cuts', scope],
    queryFn: async () => (await api.get<CashCut[]>('/shifts/cuts', { params: { limit: 15, scope } })).data,
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

  // Al tener un turno abierto, consulta sus opciones de cierre (Opción B). Ante cualquier
  // error ≠ 200 usa el fallback conservador (primer turno + confirmación fuerte para FIN_DIA).
  useEffect(() => {
    if (openedShiftId == null) { setOpts(null); return; }
    let alive = true;
    closeOptions(openedShiftId)
      .then((o) => { if (alive) setOpts(o); })
      .catch(() => { if (alive) setOpts(CLOSE_OPTS_FALLBACK); });
    return () => { alive = false; };
  }, [openedShiftId]);

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
        countedCash: Number(countedCash), mode: closeMode,
      })).data,
    onSuccess: (data) => {
      setClosure(data);
      setOpenedShiftId(null);
      setCountedCash('');
      setConfirmFinDia(false);
      setVoucher(cutVoucher(data, 'Z'));
      queryClient.invalidateQueries({ queryKey: ['cash-cuts'] });
      toast.success(
        closeMode === 'FIN_DIA' ? 'Fin del día cerrado' : 'Turno cerrado',
        closeMode === 'FIN_DIA'
          ? `Se generó el corte Z y el pan sobrante pasó a merma. Diferencia del arqueo: ${money(data.difference)}.`
          : `Se generó el corte Z. El pan sobrante queda vendible para la siguiente cajera. Diferencia del arqueo: ${money(data.difference)}.`,
      );
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

  // Reabrir un turno cerrado por error (solo el dueño). Anula el corte y revierte el turno a
  // abierto; la cajera vuelve a vender. En error 409, se pinta el mensaje del ProblemDetail.
  const reopenShift = useMutation({
    mutationFn: async (vars: { shiftId: number; reason: string }) =>
      (await api.post<ReopenResult>(`/shifts/${vars.shiftId}/reopen`, { reason: vars.reason })).data,
    onSuccess: (data) => {
      setReopenTarget(null);
      // Refresca el historial y desbloquea las superficies de la cajera.
      queryClient.invalidateQueries({ queryKey: ['cash-cuts'] });
      queryClient.invalidateQueries({ queryKey: ['shift', 'day-status'] });
      queryClient.invalidateQueries({ queryKey: ['cashier', 'my-day'] });
      queryClient.invalidateQueries({ queryKey: ['shift', 'active'] });
      toast.success(
        data.alreadyOpen ? 'El turno ya estaba abierto' : 'Turno reabierto',
        data.alreadyOpen
          ? 'No se hicieron cambios; la cajera ya podía continuar.'
          : 'La cajera puede continuar donde quedó. El corte anterior quedó anulado.',
      );
    },
    onError: (err) => {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
        ?? 'No se pudo reabrir el turno. Inténtalo de nuevo.';
      toast.error('No se pudo reabrir el turno', detail);
    },
  });

  const activeBranches = (branches.data ?? []).filter((b) => b.active);
  // Señal efectiva de cierre (con fallback conservador si aún no llegó o falló la consulta).
  const closeOpts = opts ?? CLOSE_OPTS_FALLBACK;

  // Comparación de conteos: por (shiftId, isTest) localiza el Z ANULADO (voided=true) más reciente
  // del turno, para emparejarlo con el Z vigente en su propia tarjeta. La guardia de modo prueba
  // del backend garantiza que ambos comparten isTest; aquí se agrupa con lo que el scope activo ya
  // trajo (no se reconsulta /cuts).
  const voidedZByShift = useMemo(() => {
    const map = new Map<string, CashCut>();
    for (const c of cuts.data ?? []) {
      if (c.cutType !== 'Z' || !c.voided) continue;
      const key = `${c.shiftId}:${c.isTest}`;
      const prev = map.get(key);
      // Conserva el Z anulado más reciente (por fecha de creación).
      if (!prev || new Date(c.createdAt) > new Date(prev.createdAt)) map.set(key, c);
    }
    return map;
  }, [cuts.data]);

  // Tono del chip de diferencia: verde si cuadra, ámbar faltante leve, rojo faltante relevante.
  const diffTone = (diff: number | null): string => {
    if (diff == null || diff === 0) return 'c-green';
    if (diff > 0) return 'c-green'; // sobra: no es pérdida
    return diff >= -50 ? 'c-amber' : 'c-red'; // faltante: leve vs relevante (posible pérdida)
  };

  return (
    <div>
      <h1 className="page-title">Cortes de caja</h1>
      <p className="page-sub">Elige sucursal y caja, abre turno, registra efectivo y haz el arqueo</p>

      <InfoBanner icon={<Receipt size={18} />} title="Cortes del día" tone="brand">
        Aquí ves tus cortes del día. Un turno cerrado por error puede reabrirse el mismo día.
      </InfoBanner>

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
            <div style={{ display: 'grid', gap: 'var(--space-3)', marginTop: 'var(--space-3)', maxWidth: 460 }}>
              <label className="field"><span>Efectivo contado</span>
                <div className="money-field">
                  <span className="money-prefix">$</span>
                  <input type="text" inputMode="decimal" value={countedCash}
                    onChange={(e) => setCountedCash(e.target.value.replace(/^0+(?=\d)/, ''))}
                    placeholder="0.00" />
                </div>
              </label>
              {/* Dos modos (Opción B): relevo directo (no manda pan a merma) o fin de día (confirma). */}
              <div className="ccut-close-actions">
                <motion.button
                  className={`pos-confirm pos-confirm-sky${closeOpts.suggestedMode === 'FIN_DIA' ? ' is-secondary' : ''}`}
                  disabled={closeShift.isPending}
                  onClick={() => { setCloseMode('TURNO'); closeShift.mutate(); }}
                  whileHover={closeShift.isPending ? undefined : pressable.whileHover}
                  whileTap={closeShift.isPending ? undefined : pressable.whileTap}
                  transition={pressable.transition}>
                  <Sunrise size={15} /> {closeShift.isPending && closeMode === 'TURNO' ? 'Cerrando…' : 'Cerrar turno (relevo)'}
                </motion.button>
                <motion.button
                  className={`pos-confirm pos-confirm-amber${closeOpts.suggestedMode === 'FIN_DIA' ? '' : ' is-secondary'}`}
                  disabled={closeShift.isPending}
                  onClick={() => { setCloseMode('FIN_DIA'); setConfirmFinDia(true); }}
                  whileHover={closeShift.isPending ? undefined : pressable.whileHover}
                  whileTap={closeShift.isPending ? undefined : pressable.whileTap}
                  transition={pressable.transition}>
                  <Moon size={15} /> {closeShift.isPending && closeMode === 'FIN_DIA' ? 'Cerrando…' : 'Cerrar fin del día'}
                </motion.button>
              </div>
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
        <div className="sec-title-row">
          <h3 className="sec-title"><FileText size={18} /> Historial de cortes (X / Z)</h3>
          <ScopeSelector value={scope} onChange={setScopePick} />
        </div>

        {(cuts.data ?? []).length === 0 ? (
          <div className="ccut-empty">
            <Receipt size={26} />
            <strong>Aún no hay cortes hoy</strong>
            <span>Cierra un turno para generar tu corte Z.</span>
          </div>
        ) : (
          <div className="ccuts">
            <div className="ccut-head">
              <span>Folio</span>
              <span>Tipo</span>
              <span>Turno</span>
              <span className="ccut-num">Ventas</span>
              <span className="ccut-num">Total</span>
              <span className="ccut-num">Diferencia</span>
              <span>Estado / fecha</span>
              <span></span>
            </div>
            <div className="ccut-body">
              {(cuts.data ?? []).map((c) => {
                const key = `${c.shiftId}:${c.isTest}`;
                const voidedPair = voidedZByShift.get(key);
                // La comparación se pinta una sola vez, en la tarjeta del Z VIGENTE del turno,
                // cuando existe su par anulado.
                const showCompare = c.cutType === 'Z' && !c.voided
                  && voidedPair != null && voidedPair.countedCash != null && c.countedCash != null;
                const prev = voidedPair?.countedCash ?? null;
                const now = c.countedCash;
                const delta = (showCompare && prev != null && now != null) ? now - prev : null;
                return (
                  <div className="ccut-card" key={c.id}>
                    <div className={`ccut-row ${c.voided ? 'is-voided' : ''}`}>
                      <span className="ccut-folio">{c.folio}</span>
                      <span>
                        <span className={`ccut-chip ${c.cutType === 'Z' ? 'c-brand' : 'c-violet'}`}>
                          Corte {c.cutType}
                        </span>
                      </span>
                      <span>#{c.shiftId}</span>
                      <span className="ccut-num">{c.salesCount}</span>
                      <span className="ccut-num">{money(c.totalSales)}</span>
                      <span className="ccut-num">
                        {c.difference != null ? (
                          <span className={`ccut-chip ${diffTone(c.difference)}`}>{money(c.difference)}</span>
                        ) : (
                          <span className="ccut-chip c-neutral">—</span>
                        )}
                      </span>
                      <span>
                        {c.voided ? (
                          <span className="ccut-chip c-amber">Anulado por reapertura</span>
                        ) : (
                          <span className="ccut-when">
                            {new Date(c.createdAt).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' })}
                          </span>
                        )}
                      </span>
                      <span className="ccut-actions">
                        <button className="icon-btn" onClick={() => setVoucher(cutRowVoucher(c))} aria-label="Imprimir">
                          <Printer size={15} />
                        </button>
                      </span>
                    </div>

                    {/* Subtexto del corte anulado: quién/cuándo/motivo (traza sin abrir auditoría). */}
                    {c.voided && (
                      <p className="ccut-voided-note">
                        Anulado{c.voidedBy ? ` por ${c.voidedBy}` : ''}
                        {c.voidedAt ? ` el ${new Date(c.voidedAt).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' })}` : ''}
                        {c.voidedReason ? ` · Motivo: ${c.voidedReason}` : ''}
                      </p>
                    )}

                    {/* Comparación de conteos (anulado vs nuevo) + diferencia con chip de color. */}
                    {showCompare && (
                      <div className="ccut-compare">
                        <div className="ccut-compare-item">
                          <span className="ccut-compare-label">Contó anterior (anulado)</span>
                          <span className="ccut-compare-value">{money(prev ?? 0)}</span>
                        </div>
                        <div className="ccut-compare-item">
                          <span className="ccut-compare-label">Contó nuevo</span>
                          <span className="ccut-compare-value">{money(now ?? 0)}</span>
                        </div>
                        <div className="ccut-compare-item">
                          <span className="ccut-compare-label">Diferencia</span>
                          <span className={`ccut-chip ${diffTone(delta)}`}>{money(delta ?? 0)}</span>
                        </div>
                      </div>
                    )}

                    {/* Botón Reabrir turno: solo el dueño y solo si el backend lo marca reabrible. */}
                    {owner && c.reopenable && (
                      <div style={{ padding: '6px 2px 0', display: 'flex', justifyContent: 'flex-end' }}>
                        <button className="ccut-reopen-btn" onClick={() => setReopenTarget(c)}>
                          <RotateCcw size={14} /> Reabrir turno
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </motion.div>

      {/* Confirmación fuerte del fin de día (H4): FIN_DIA en esta pantalla SIEMPRE confirma. */}
      <ConfirmFinDiaModal
        open={confirmFinDia}
        onlyShiftSoFar={closeOpts.onlyShiftSoFar}
        otherOpenInBranch={closeOpts.otherOpenInBranch}
        onConfirm={() => closeShift.mutate()}
        onCancel={() => setConfirmFinDia(false)}
      />

      <ReopenShiftModal
        open={reopenTarget != null}
        shiftId={reopenTarget?.shiftId ?? null}
        folio={reopenTarget?.folio ?? null}
        hadFinDiaWaste={reopenTarget?.hadFinDiaWaste ?? false}
        busy={reopenShift.isPending}
        onConfirm={(reason) => {
          if (reopenTarget) reopenShift.mutate({ shiftId: reopenTarget.shiftId, reason });
        }}
        onCancel={() => setReopenTarget(null)}
      />

      {voucher && <PrintableVoucher data={voucher} onDone={() => setVoucher(null)} />}
    </div>
  );
}
