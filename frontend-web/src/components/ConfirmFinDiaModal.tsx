import { AnimatePresence, motion } from 'motion/react';
import { Moon, TriangleAlert } from 'lucide-react';
import { InfoBanner } from '@/components/InfoBanner';
import '@/pages/pos.css';

/**
 * Modal PREMIUM de confirmación fuerte para el cierre de fin de día (FIN_DIA).
 *
 * <p>
 * {@code FIN_DIA} dispara el candado de merma sobre todo el stock normal de la sucursal
 * y bloquea nuevas aperturas; es la acción más destructiva del flujo y jamás debe lanzarse
 * "de un clic". Por eso TODO camino de cierre de día pasa por este modal antes de ejecutar
 * el cierre (regla inviolable §2 punto 7 del diseño). Es un modal premium (overlay + motion,
 * tono amber, icono Moon), NO un {@code window.confirm}.
 *
 * <p>
 * El copy se diferencia por {@code onlyShiftSoFar} (único turno vs primero con relevo en
 * curso vs turno posterior) y, si {@code otherOpenInBranch}, añade la advertencia multi-caja.
 * Lo consumen tanto el POS ({@code CloseShiftModal}) como {@code CashierPage}.
 */
export interface ConfirmFinDiaModalProps {
  open: boolean;
  onlyShiftSoFar: boolean;
  otherOpenInBranch: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmFinDiaModal({
  open, onlyShiftSoFar, otherOpenInBranch, onConfirm, onCancel,
}: ConfirmFinDiaModalProps) {
  // Copy diferenciado (§7.3): único turno vs primero con relevo en curso vs posterior normal.
  const bodyText = onlyShiftSoFar
    ? 'Eres el único turno de hoy. Si cierras el día, no habrá más turnos y el pan sobrante pasará a merma. ¿Seguro?'
    : 'Cerrar el día manda el pan sobrante a merma y deja la caja cerrada hasta mañana. ¿Seguro?';

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="pos-modal-overlay" onClick={onCancel}
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
          <motion.div className="pos-modal pos-modal-sm pos-findia-confirm" onClick={(e) => e.stopPropagation()}
            initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.97 }}>
            <div className="pos-modal-head">
              <h3 style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <Moon size={18} /> ¿Cerrar el fin del día?
              </h3>
            </div>

            <div style={{ padding: 'var(--space-4) var(--space-5)', display: 'grid', gap: 'var(--space-3)' }}>
              <InfoBanner icon={<Moon size={18} />} title="Cerrar fin del día" tone="amber">
                {bodyText}
              </InfoBanner>

              {otherOpenInBranch && (
                <InfoBanner icon={<TriangleAlert size={18} />} title="Hay otra caja abierta" tone="amber">
                  Hay otra caja abierta en esta sucursal. Cerrar el día mandará a merma el pan de
                  toda la sucursal, incluido el que la otra caja aún vende.
                </InfoBanner>
              )}
            </div>

            <div className="pos-modal-actions">
              <button className="btn-ghost" onClick={onCancel}>Volver</button>
              <button className="pos-confirm pos-confirm-amber" onClick={onConfirm}>
                <Moon size={15} /> Sí, cerrar el día
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
