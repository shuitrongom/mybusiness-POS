import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { LockKeyhole, Wallet, DoorOpen, AlertTriangle, Check, RefreshCw, BadgeCheck } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { PrintableVoucher } from '@/components/PrintableVoucher';
import { type VoucherData } from '@/components/Voucher';
import '@/pages/pos.css';

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
  const [float, setFloat] = useState('0');
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
                <input type="number" step="0.01" min={0} value={float} onChange={(e) => setFloat(e.target.value)} autoFocus />
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

interface Closure {
  openingFloat: number; cashSales: number; cardSales: number; transferSales: number;
  cashIn: number; cashOut: number; expectedCash: number; countedCash: number; difference: number;
}

/** Modal de cierre/entrega de caja con arqueo y ADVERTENCIA clara de que no podrá vender después. */
function CloseShiftModal({ open, shift, onClose, onClosed }:
  { open: boolean; shift: ActiveShift; onClose: () => void; onClosed: () => void }) {
  const [counted, setCounted] = useState('');
  const [busy, setBusy] = useState(false);
  const [closure, setClosure] = useState<Closure | null>(null);
  const [voucher, setVoucher] = useState<VoucherData | null>(null);

  const buildVoucher = (c: Closure): VoucherData => ({
    title: 'CORTE DE CAJA Z',
    storeName: shift.registerName ?? 'Caja',
    folio: `Z-${shift.shiftId}`,
    dateTime: new Date(),
    branchName: shift.registerName,
    rows: [
      { label: 'Fondo inicial', value: money(c.openingFloat) },
      { label: 'Ventas efectivo', value: money(c.cashSales) },
      { label: 'Ventas tarjeta', value: money(c.cardSales) },
      { label: 'Ventas transferencia', value: money(c.transferSales) },
      { label: 'Entradas de efectivo', value: money(c.cashIn) },
      { label: 'Salidas / retiros', value: `-${money(c.cashOut)}` },
      { label: 'Efectivo esperado', value: money(c.expectedCash), strong: true },
      { label: 'Efectivo contado', value: money(c.countedCash), strong: true },
      { label: 'Diferencia', value: money(c.difference), strong: true, danger: c.difference !== 0 },
    ],
    signature: true,
    footer: 'Conserva este corte como respaldo del turno.',
  });

  const doClose = async () => {
    setBusy(true);
    try {
      const { data } = await api.post<Closure>(`/shifts/${shift.shiftId}/close`, { countedCash: Number(counted) || 0 });
      setClosure(data);
      // Imprime automáticamente el comprobante del corte Z en la impresora de tickets.
      setVoucher(buildVoucher(data));
      // Registra la evidencia del corte Z (bitácora de comprobantes para el dueño).
      api.post('/documents', {
        docType: 'CUT_Z', folio: `Z-${shift.shiftId}`, title: 'Corte de caja Z',
        shiftId: shift.shiftId, branchId: shift.branchId, amount: data.expectedCash, reprint: false,
        payload: { ...data, registerName: shift.registerName },
      }).catch(() => {});
      toast.success('Caja cerrada', 'Se generó e imprimió tu corte Z. Ya no podrás vender más hoy.');
    } catch {
      toast.error('No se pudo cerrar la caja', 'Inténtalo de nuevo.');
    } finally { setBusy(false); }
  };

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="pos-modal-overlay" onClick={closure ? undefined : onClose}
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
          <motion.div className="pos-modal pos-modal-sm" onClick={(e) => e.stopPropagation()}
            initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.97 }}>
            <div className="pos-modal-head">
              <h3 style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}><DoorOpen size={18} /> Cerrar / entregar caja</h3>
              {!closure && <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>}
            </div>

            {!closure ? (
              <>
                <div style={{ padding: 'var(--space-4) var(--space-5)' }}>
                  <div className="pos-shift-warning">
                    <AlertTriangle size={18} />
                    <span><strong>Importante:</strong> al cerrar tu caja se genera tu corte y <strong>no podrás registrar más ventas en este turno el día de hoy</strong>. Si es cambio de cajero, el siguiente cajero deberá abrir su propia caja.</span>
                  </div>
                  <label className="field" style={{ marginTop: 'var(--space-3)' }}><span>Efectivo contado en caja (arqueo)</span>
                    <input type="number" step="0.01" min={0} value={counted} onChange={(e) => setCounted(e.target.value)} autoFocus />
                  </label>
                </div>
                <div className="pos-modal-actions">
                  <button className="btn-ghost" onClick={onClose}>Cancelar</button>
                  <button className="pos-confirm" onClick={doClose} disabled={busy}>
                    <Check size={15} /> {busy ? 'Cerrando…' : 'Cerrar caja'}
                  </button>
                </div>
              </>
            ) : (
              <>
                <div style={{ padding: 'var(--space-4) var(--space-5)' }} className="pos-closure">
                  <div><span>Fondo inicial</span><strong>{money(closure.openingFloat)}</strong></div>
                  <div><span>Ventas efectivo</span><strong>{money(closure.cashSales)}</strong></div>
                  <div><span>Ventas tarjeta</span><strong>{money(closure.cardSales)}</strong></div>
                  <div><span>Entradas / salidas</span><strong>{money(closure.cashIn)} / {money(closure.cashOut)}</strong></div>
                  <div><span>Efectivo esperado</span><strong>{money(closure.expectedCash)}</strong></div>
                  <div><span>Efectivo contado</span><strong>{money(closure.countedCash)}</strong></div>
                  <div className={closure.difference === 0 ? '' : 'pos-closure-diff'}>
                    <span>Diferencia</span><strong>{money(closure.difference)}</strong>
                  </div>
                </div>
                <div className="pos-modal-actions">
                  <button className="btn-ghost" onClick={() => closure && setVoucher(buildVoucher(closure))}>
                    <DoorOpen size={15} /> Reimprimir corte
                  </button>
                  <button className="pos-confirm" onClick={onClosed}><RefreshCw size={15} /> Entendido</button>
                </div>
              </>
            )}
          </motion.div>
        </motion.div>
      )}
      {voucher && <PrintableVoucher data={voucher} onDone={() => setVoucher(null)} />}
    </AnimatePresence>
  );
}
