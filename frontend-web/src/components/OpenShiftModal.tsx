import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { LockKeyhole, Check } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import '@/pages/pos.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/** Turno de caja activo del cajero (fuente de verdad del POS para ligar las ventas). */
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
/** Cachea (o limpia) el turno activo en localStorage para que el POS lo reconozca al entrar. */
export function cacheShift(s: ActiveShift | null) {
  if (s) localStorage.setItem(SHIFT_KEY, JSON.stringify(s));
  else localStorage.removeItem(SHIFT_KEY);
}

/**
 * Modal premium de apertura de caja con fondo inicial. Reutilizable: es la MISMA pieza que usa el
 * POS (PosShift) como puerta obligatoria y el inicio del cajero para abrir caja y entrar a vender.
 * Al abrir con éxito (POST /shifts/open) invoca onOpened con el turno activo; el consumidor decide
 * qué hacer (cachear el turno, navegar al POS, etc.). No cambia su contrato ni su comportamiento.
 */
export function OpenShiftModal({ open, branchId, onOpened }: { open: boolean; branchId: number; onOpened: (s: ActiveShift) => void }) {
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
