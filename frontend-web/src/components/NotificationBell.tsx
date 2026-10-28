import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { Bell, CheckCheck } from 'lucide-react';
import {
  useUnreadCount, useInbox, useMarkRead, useMarkAllRead,
  NOTIF_META, DEFAULT_NOTIF_META, type NotificationView,
} from '@/lib/notifications';
import { fmtDateTime } from '@/pages/supportShared';
import { useSession } from '@/store/session';
import { decodeToken, isSuperAdmin } from '@/lib/jwt';
import { popIn } from '@/lib/motion';
import './notification-bell.css';

/**
 * Campanita del centro de notificaciones en la barra superior. Badge con el conteo de no leídas
 * (poll ligero cada 45 s, pausado en segundo plano) y un popover premium con la bandeja en
 * filas-tarjeta. Al pulsar una notificación se marca como leída (optimista) y, si apunta a un
 * ticket de soporte, navega al deep-link correspondiente. El copy varía por rol (negocio vs
 * super-admin), igual que el botón de "marcar todas".
 */
export function NotificationBell() {
  const token = useSession((s) => s.token);
  const superAdmin = isSuperAdmin(decodeToken(token));
  const navigate = useNavigate();

  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const unread = useUnreadCount();
  const inbox = useInbox();
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();

  // La bandeja completa solo se consulta cuando el panel está abierto (lazy).
  useEffect(() => {
    if (open) inbox.refetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Cerrar al hacer clic fuera o con Escape.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  // Badge: reconcilia el poll ligero con el unreadCount de la bandeja cuando está abierta (N1).
  const count = (open && inbox.data) ? inbox.data.unreadCount : (unread.data ?? 0);
  const badge = count > 99 ? '99+' : String(count);

  const items = inbox.data?.items ?? [];
  const emptyCopy = superAdmin
    ? 'Bandeja del equipo PuntoNube. Aquí verás los reportes nuevos y las respuestas de los negocios; se marcan como atendidos al resolver el ticket.'
    : 'Aquí verás las respuestas del equipo PuntoNube a los reportes que creaste.';
  const markAllLabel = superAdmin ? 'Marcar todas como atendidas' : 'Marcar todas como leídas';

  const onClickItem = (n: NotificationView) => {
    if (!n.read) markRead.mutate(n.publicId);
    const meta = NOTIF_META[n.type];
    if (n.refType === 'support_ticket' && n.refId && meta?.navRoute) {
      setOpen(false);
      navigate(meta.navRoute(n.refId, superAdmin));
    }
  };

  return (
    <div className="notif-bell" ref={rootRef}>
      <button
        type="button"
        className="notif-bell-btn"
        onClick={() => setOpen((o) => !o)}
        aria-label={count > 0 ? `Notificaciones, ${count} sin leer` : 'Notificaciones'}
        aria-expanded={open}
        aria-haspopup="dialog"
        title="Notificaciones"
      >
        <Bell size={18} strokeWidth={1.9} />
        {count > 0 && <span className="notif-badge" aria-hidden>{badge}</span>}
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            className="notif-pop"
            role="dialog"
            aria-label="Centro de notificaciones"
            variants={popIn}
            initial="hidden"
            animate="visible"
            exit="exit"
          >
            <div className="notif-pop-head">
              <strong>Notificaciones</strong>
              {items.some((n) => !n.read) && (
                <button
                  type="button"
                  className="notif-markall"
                  onClick={() => markAllRead.mutate()}
                  disabled={markAllRead.isPending}
                >
                  <CheckCheck size={14} /> {markAllLabel}
                </button>
              )}
            </div>

            <div className="notif-list">
              {inbox.isLoading ? (
                <p className="notif-loading">Cargando…</p>
              ) : items.length === 0 ? (
                <div className="notif-empty">
                  <div className="notif-empty-icon" aria-hidden><Bell size={30} /></div>
                  <p>{emptyCopy}</p>
                </div>
              ) : (
                items.map((n) => {
                  const meta = NOTIF_META[n.type] ?? DEFAULT_NOTIF_META;
                  const navegable = n.refType === 'support_ticket' && !!n.refId && !!NOTIF_META[n.type]?.navRoute;
                  const when = fmtDateTime(n.createdAt);
                  return (
                    <button
                      key={n.publicId}
                      type="button"
                      className={`notif-item tone-${meta.tone} ${n.read ? '' : 'is-unread'} ${navegable ? '' : 'is-static'}`}
                      onClick={() => onClickItem(n)}
                    >
                      {!n.read && <span className="notif-dot" aria-hidden />}
                      <span className="notif-item-icon" aria-hidden>{meta.icon}</span>
                      <span className="notif-item-body">
                        <span className="notif-item-title">{n.title}</span>
                        {n.body && <span className="notif-item-text">{n.body}</span>}
                        <span className="notif-item-time notif-num">{when.full}</span>
                      </span>
                    </button>
                  );
                })
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
