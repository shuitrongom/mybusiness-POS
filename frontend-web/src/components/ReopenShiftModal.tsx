import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { RotateCcw, AlertTriangle, X } from 'lucide-react';
import { InfoBanner } from '@/components/InfoBanner';
import '@/pages/pos.css';

/**
 * Modal premium de REAPERTURA DE TURNO (solo el dueño lo ve/ejecuta). Explica qué
 * pasará (el turno vuelve a abierto y el corte actual queda ANULADO, conservado en
 * el histórico), advierte sobre la merma de fin de día cuando aplica, y exige un
 * MOTIVO obligatorio (textarea con placeholder tenue, nunca texto pre-escrito). El
 * botón confirmar se mantiene deshabilitado mientras el motivo esté vacío, espejo
 * del {@code @NotBlank} del backend.
 */
export function ReopenShiftModal({
  open, shiftId, folio, hadFinDiaWaste, busy, onConfirm, onCancel,
}: {
  open: boolean;
  shiftId: number | null;
  folio: string | null;
  hadFinDiaWaste: boolean;
  busy: boolean;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}) {
  const [reason, setReason] = useState('');

  // Limpia el motivo cada vez que se abre, para no arrastrar el de una reapertura previa.
  useEffect(() => {
    if (open) setReason('');
  }, [open]);

  const trimmed = reason.trim();

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="pos-modal-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
          <motion.div className="pos-modal pos-modal-picker" onClick={(e) => e.stopPropagation()}
            initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.97 }}>
            <div className="pos-modal-head">
              <h3 style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <RotateCcw size={18} /> Reabrir turno {folio ? `(${folio})` : `#${shiftId ?? ''}`}
              </h3>
              <button className="pos-modal-close" onClick={onCancel} aria-label="Cerrar" disabled={busy}>
                <X size={18} />
              </button>
            </div>

            <div style={{ padding: 'var(--space-4) var(--space-5)' }}>
              <InfoBanner icon={<RotateCcw size={18} />} title="Reabrir turno" tone="amber">
                El turno volverá a estado abierto y la cajera podrá continuar donde quedó.
              </InfoBanner>

              <p style={{ margin: 'var(--space-4) 0 0', color: 'var(--text-muted)' }}>
                El corte actual quedará <strong>anulado</strong> y se conservará en el histórico
                (nada se borra). Al cerrar de nuevo se generará un corte nuevo. Las ventas y los
                movimientos de efectivo del turno se mantienen intactos.
              </p>

              {hadFinDiaWaste && (
                <div style={{
                  display: 'flex', gap: 10, alignItems: 'flex-start',
                  marginTop: 'var(--space-4)', padding: 'var(--space-3) var(--space-4)',
                  borderRadius: 12, border: '1px solid var(--amber-600, #d97706)',
                  background: 'color-mix(in srgb, var(--amber-500, #f59e0b) 12%, transparent)',
                }}>
                  <AlertTriangle size={18} style={{ color: 'var(--amber-600, #d97706)', flexShrink: 0, marginTop: 2 }} />
                  <span style={{ color: 'var(--text)', fontSize: 'var(--fs-sm)' }}>
                    Este turno se cerró como <strong>Fin de día</strong>: su pan sobrante ya pasó a
                    merma y la reapertura <strong>NO</strong> lo devuelve al inventario. Si la merma
                    fue indebida, ajústala manualmente en Inventario → Merma.
                  </span>
                </div>
              )}

              <label className="field" style={{ marginTop: 'var(--space-4)' }}>
                <span>Motivo de la reapertura (obligatorio)</span>
                <textarea
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Ej. La cajera cerró por error antes de tiempo"
                  rows={3}
                  maxLength={300}
                  autoFocus
                  style={{
                    resize: 'vertical', width: '100%', padding: 'var(--space-3)',
                    borderRadius: 10, border: '1px solid var(--border)', background: 'var(--surface-2)',
                    color: 'var(--text)', font: 'inherit',
                  }}
                />
              </label>
            </div>

            <div className="pos-modal-actions">
              <button className="btn-ghost" onClick={onCancel} disabled={busy}>
                Cancelar
              </button>
              <button className="pos-confirm" onClick={() => onConfirm(trimmed)} disabled={busy || trimmed === ''}>
                <RotateCcw size={15} /> {busy ? 'Reabriendo…' : 'Reabrir turno'}
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
