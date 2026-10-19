import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { LockKeyhole, Wallet, DoorOpen, Check, BadgeCheck, Moon, Sunrise } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { InfoBanner } from '@/components/InfoBanner';
import { DaySummaryModal, type DaySummary } from '@/components/DaySummaryModal';
import '@/pages/pos.css';

/** Modo de cierre de caja: relevo intermedio (no manda sobrante a merma) o fin de jornada (sí). */
type CloseMode = 'TURNO' | 'FIN_DIA';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

export interface ActiveShift {
  shiftId: number;
  cashRegisterId: number;
  branchId: number | null;
  registerName: string | null;
  openingFloat: number;
  openedAt: string;
  businessDate: string;
}
interface CashRegister { id: number; name: string; branchId?: number; }

const SHIFT_KEY = 'mbs.shift';

/** Lee el turno activo cacheado (para arranque optimista). */
export function cachedShift(): ActiveShift | null {
  try { return JSON.parse(localStorage.getItem(SHIFT_KEY) ?? 'null'); } catch { return null; }
}
function cacheShift(s: ActiveShift | null) {
  if (s) localStorage.setItem(SHIFT_KEY, JSON.stringify(s));
  else localStorage.removeItem(SHIFT_KEY);
}

/**
 * Barra de turno del POS + control de apertura/cierre de caja. Es la puerta del punto de venta:
 * mientras no haya un turno abierto muestra un modal OBLIGATORIO de apertura con fondo inicial.
 * Notifica al POS el turno activo (para ligar las ventas) vía onShiftChange.
 */
export function PosShift({ branchId, onShiftChange }: { branchId: number; onShiftChange: (s: ActiveShift | null) => void }) {
  const [shift, setShift] = useState<ActiveShift | null>(cachedShift());
  const [loading, setLoading] = useState(true);
  const [openModal, setOpenModal] = useState(false);
  const [closeModal, setCloseModal] = useState(false);
  // closedToday = el cajero ya cerró su caja hoy: NO puede volver a abrir ni vender hasta mañana.
  const [closedToday, setClosedToday] = useState(false);

  const refresh = async () => {
    try {
      // Estado del día primero: si el cajero ya cerró, se bloquea el POS por completo.
      const day = await api.get<{ closedToday: boolean }>('/shifts/day-status').then((r) => r.data).catch(() => ({ closedToday: false }));
      if (day.closedToday) {
        setClosedToday(true);
        setShift(null); cacheShift(null); onShiftChange(null); setOpenModal(false);
        return;
      }
      const res = await api.get<ActiveShift>('/shifts/active');
      const active = res.status === 204 || !res.data ? null : res.data;
      setShift(active);
      cacheShift(active);
      onShiftChange(active);
      setOpenModal(active === null);
    } catch {
      // Sin conexión: si hay turno cacheado se respeta para poder seguir vendiendo offline.
      const cached = cachedShift();
      setShift(cached);
      onShiftChange(cached);
      setOpenModal(cached === null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { refresh(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  const onOpened = (s: ActiveShift) => {
    setShift(s); cacheShift(s); onShiftChange(s); setOpenModal(false);
  };
  // Al cerrar la caja, el turno queda TERMINADO por hoy: no se reabre el modal de apertura;
  // se marca closedToday y el POS queda bloqueado hasta el día siguiente.
  const onClosed = () => {
    setShift(null); cacheShift(null); onShiftChange(null); setCloseModal(false); setClosedToday(true);
  };

  if (loading) return null;

  // Estado terminal: caja cerrada por hoy. Bloquea todo el POS con mensaje claro.
  if (closedToday) {
    return (
      <div className="pos-shift-bar pos-shift-closed">
        <BadgeCheck size={18} />
        <div>
          <strong>Tu corte de caja de hoy ya fue cerrado.</strong>
          <span> No puedes registrar más ventas hoy. Tu caja se podrá abrir hasta el día siguiente. Consulta tu corte en Cortes de caja.</span>
        </div>
      </div>
    );
  }

  return (
    <>
      {shift && (
        <div className="pos-shift-bar">
          <span className="pos-shift-chip"><Wallet size={15} /> Caja: <strong>{shift.registerName ?? '—'}</strong></span>
          <span className="pos-shift-chip">Fondo inicial: <strong>{money(shift.openingFloat)}</strong></span>
          <span className="pos-shift-chip pos-shift-open">Turno abierto</span>
          <button className="pos-shift-close-btn" onClick={() => setCloseModal(true)}>
            <DoorOpen size={15} /> Cerrar / entregar caja
          </button>
        </div>
      )}

      <OpenShiftModal open={openModal} branchId={branchId} onOpened={onOpened} />
      {shift && <CloseShiftModal open={closeModal} shift={shift} onClose={() => setCloseModal(false)} onClosed={onClosed} />}
    </>
  );
}

/** Modal OBLIGATORIO de apertura de caja con fondo inicial. No se puede cerrar sin abrir turno. */
function OpenShiftModal({ open, branchId, onOpened }: { open: boolean; branchId: number; onOpened: (s: ActiveShift) => void }) {
  const [registers, setRegisters] = useState<CashRegister[]>([]);
  const [registerId, setRegisterId] = useState<number>(0);
  const [float, setFloat] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    api.get<CashRegister[]>('/cash-registers').then((r) => {
      setRegisters(r.data);
      setRegisterId((cur) => cur || r.data[0]?.id || 0);
    }).catch(() => setRegisters([]));
  }, [open]);

  const preset = (v: number) => setFloat(String(v));

  const openShift = async () => {
    if (!registerId) { toast.error('Elige una caja', 'Selecciona la caja registradora.'); return; }
    setBusy(true);
    try {
      const { data } = await api.post<{ shiftId: number }>('/shifts/open', {
        cashRegisterId: registerId, openingFloat: Number(float) || 0, branchId,
      });
      const reg = registers.find((r) => r.id === registerId);
      onOpened({
        shiftId: data.shiftId, cashRegisterId: registerId, branchId,
        registerName: reg?.name ?? null, openingFloat: Number(float) || 0,
        openedAt: new Date().toISOString(), businessDate: new Date().toISOString().slice(0, 10),
      });
      toast.success('Caja abierta', `Fondo inicial: ${money(Number(float) || 0)}.`);
    } catch (err) {
      const msg = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
        ?? 'No se pudo abrir la caja. Esa caja puede tener un turno abierto.';
      toast.error('No se pudo abrir la caja', msg);
    } finally { setBusy(false); }
  };

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="pos-modal-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
          <motion.div className="pos-modal pos-modal-sm" onClick={(e) => e.stopPropagation()}
            initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.97 }}>
            <div className="pos-modal-head">
              <h3 style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}><LockKeyhole size={18} /> Abrir caja</h3>
            </div>
            <div style={{ padding: 'var(--space-4) var(--space-5)' }}>
              <p style={{ marginTop: 0, color: 'var(--text-muted)' }}>
                Para empezar a vender, abre tu caja y registra el dinero con el que inicias (fondo de caja).
                Este monto es la base para tu corte al final del turno.
              </p>
              <label className="field"><span>Caja registradora</span>
                <select value={registerId} onChange={(e) => setRegisterId(Number(e.target.value))}>
                  {registers.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
                </select>
              </label>
              <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Fondo inicial de caja</span>
                <input type="number" step="0.01" min={0} value={float} placeholder="0.00"
                  onChange={(e) => {
                    // Quita ceros a la izquierda (ej. "0100" -> "100", "01" -> "1") para que no
                    // se quede pegado el 0 inicial al teclear. Conserva decimales ("0.5").
                    let v = e.target.value;
                    if (/^0\d/.test(v)) v = v.replace(/^0+/, '');
                    setFloat(v);
                  }} autoFocus />
              </label>
              <div className="pos-float-presets">
                {[0, 100, 200, 500, 1000].map((v) => (
                  <button key={v} className={`pos-float-preset ${Number(float) === v ? 'is-on' : ''}`} onClick={() => preset(v)}>
                    {money(v)}
                  </button>
                ))}
              </div>
            </div>
            <div className="pos-modal-actions">
              <button className="pos-confirm" onClick={openShift} disabled={busy}>
                <Check size={15} /> {busy ? 'Abriendo…' : 'Abrir caja y comenzar'}
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/** Corte Z que devuelve POST /shifts/{id}/close (solo para la bitácora en BD, sin impresión). */
interface Closure {
  openingFloat: number; cashSales: number; cardSales: number; transferSales: number;
  voucherSales: number; cashIn: number; cashOut: number; expectedCash: number;
  countedCash: number; difference: number;
}

/**
 * Modal de cierre/entrega de caja con arqueo y ADVERTENCIA clara de que no podrá vender después.
 * El cierre NO imprime nada (ahorro de papel): solo registra el corte en BD y, al terminar, muestra
 * un resumen premium del día (DaySummaryModal) con la actividad de la jornada de la cajera.
 */
function CloseShiftModal({ open, shift, onClose, onClosed }:
  { open: boolean; shift: ActiveShift; onClose: () => void; onClosed: () => void }) {
  const [counted, setCounted] = useState('');
  const [busy, setBusy] = useState(false);
  // Tras el cierre: resumen del día (GET /shifts/my-day) + modo con el que se cerró, para el modal.
  const [summary, setSummary] = useState<DaySummary | null>(null);
  const [closedMode, setClosedMode] = useState<CloseMode>('TURNO');

  const doClose = async (mode: CloseMode) => {
    setBusy(true);
    try {
      const { data } = await api.post<Closure>(`/shifts/${shift.shiftId}/close`, {
        countedCash: Number(counted) || 0, mode,
      });
      // Registra la evidencia del corte Z (bitácora de comprobantes para el dueño). SIN impresión.
      api.post('/documents', {
        docType: 'CUT_Z', folio: `Z-${shift.shiftId}`, title: 'Corte de caja Z',
        shiftId: shift.shiftId, branchId: shift.branchId, amount: data.expectedCash, reprint: false,
        payload: { ...data, registerName: shift.registerName, closeMode: mode },
      }).catch(() => { });
      // El resumen completo del día (# ventas, unidades, ticket, split) vive en /shifts/my-day
      // (misma fuente del dashboard del cajero); /close no trae esos agregados. Si fallara, se
      // arma un resumen mínimo con lo que sí devolvió /close para no dejar al usuario sin cierre.
      const day = await api.get<DaySummary>('/shifts/my-day')
        .then((r) => (r.status === 204 || !r.data ? null : r.data))
        .catch(() => null);
      setClosedMode(mode);
      setSummary(day ?? {
        registerName: shift.registerName,
        openingFloat: data.openingFloat,
        cashSales: data.cashSales, cardSales: data.cardSales, transferSales: data.transferSales,
        voucherSales: data.voucherSales ?? 0, creditSales: 0,
        cashIn: data.cashIn, cashOut: data.cashOut,
        expectedCash: data.expectedCash, totalSales: 0, salesCount: 0, units: 0, averageTicket: 0,
        countedCash: data.countedCash, difference: data.difference,
      });
      toast.success(
        mode === 'FIN_DIA' ? 'Fin del día cerrado' : 'Turno cerrado',
        mode === 'FIN_DIA'
          ? 'Se generó tu corte Z y el pan sobrante pasó a merma. Ya no podrás vender más hoy.'
          : 'Se generó tu corte Z. El pan sobrante queda vendible para la siguiente cajera.',
      );
    } catch {
      toast.error('No se pudo cerrar la caja', 'Inténtalo de nuevo.');
    } finally { setBusy(false); }
  };

  // Tras cerrar con éxito: modal premium del día (sin impresión). "Entendido" bloquea el POS.
  if (summary) {
    return <DaySummaryModal summary={summary} mode={closedMode} onDone={onClosed} />;
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="pos-modal-overlay" onClick={onClose}
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
          <motion.div className="pos-modal pos-modal-sm" onClick={(e) => e.stopPropagation()}
            initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.97 }}>
            <div className="pos-modal-head">
              <h3 style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}><DoorOpen size={18} /> Cerrar / entregar caja</h3>
              <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
            </div>

            <div style={{ padding: 'var(--space-4) var(--space-5)', display: 'grid', gap: 'var(--space-3)' }}>
              <label className="field"><span>Efectivo contado en caja (arqueo)</span>
                <input type="number" step="0.01" min={0} value={counted} onChange={(e) => setCounted(e.target.value)} autoFocus />
              </label>
              <InfoBanner icon={<Sunrise size={18} />} title="Cerrar turno (relevo)" tone="sky">
                Hace tu corte y cierra tu turno. El pan que quede sigue disponible para la
                siguiente cajera (no pasa a merma). Hoy ya no podrás vender.
              </InfoBanner>
              <InfoBanner icon={<Moon size={18} />} title="Cerrar fin del día" tone="amber">
                Hace tu corte <strong>y manda el pan sobrante a merma automáticamente</strong>.
                Úsalo solo en el ÚLTIMO cierre del día. Hoy ya no podrás vender.
              </InfoBanner>
            </div>
            <div className="pos-modal-actions pos-close-actions">
              <button className="btn-ghost" onClick={onClose}>Cancelar</button>
              <button className="pos-confirm pos-confirm-sky" onClick={() => doClose('TURNO')} disabled={busy}>
                <Sunrise size={15} /> {busy ? 'Cerrando…' : 'Cerrar turno (relevo)'}
              </button>
              <button className="pos-confirm pos-confirm-amber" onClick={() => doClose('FIN_DIA')} disabled={busy}>
                <Moon size={15} /> {busy ? 'Cerrando…' : 'Cerrar fin del día'}
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
