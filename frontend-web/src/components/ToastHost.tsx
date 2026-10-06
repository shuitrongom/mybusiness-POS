import { AnimatePresence, motion } from 'motion/react';
import { CheckCircle2, XCircle, Info, X } from 'lucide-react';
import { useToast, type ToastKind } from '@/store/toast';
import { springSoft } from '@/lib/motion';
import './toast.css';

const ICONS: Record<ToastKind, typeof CheckCircle2> = {
  success: CheckCircle2,
  error: XCircle,
  info: Info,
};

/**
 * Contenedor global de toasts (esquina inferior derecha). Anima la entrada/salida con Motion y
 * apila las notificaciones. Se monta una sola vez, en la raíz de la app.
 */
export function ToastHost() {
  const toasts = useToast((s) => s.toasts);
  const dismiss = useToast((s) => s.dismiss);

  return (
    <div className="toast-host" role="region" aria-live="polite" aria-label="Notificaciones">
      <AnimatePresence initial={false}>
        {toasts.map((t) => {
          const Icon = ICONS[t.kind];
          return (
            <motion.div
              key={t.id}
              className={`toast toast-${t.kind}`}
              layout
              initial={{ opacity: 0, x: 40, scale: 0.95 }}
              animate={{ opacity: 1, x: 0, scale: 1, transition: springSoft }}
              exit={{ opacity: 0, x: 40, scale: 0.95, transition: { duration: 0.18 } }}
            >
              <span className="toast-icon"><Icon size={20} strokeWidth={2} /></span>
              <div className="toast-content">
                <div className="toast-title">{t.title}</div>
                {t.message && <div className="toast-message">{t.message}</div>}
              </div>
              <button className="toast-close" onClick={() => dismiss(t.id)} aria-label="Cerrar notificación">
                <X size={16} />
              </button>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
