import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'motion/react';
import {
  LifeBuoy, Bug, Lightbulb, Search, Inbox, Image as ImageIcon, Send,
} from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { InfoBanner } from '@/components/InfoBanner';
import { staggerContainer, staggerItem, popIn, quick, pressable } from '@/lib/motion';
import {
  type TicketListItem, type TicketView, type TicketStatus, type MessageView,
  TYPE_META, STATUS_META, STATUS_FILTERS, OWNER_TRANSITIONS, AUTHOR_META, fmtDateTime, deviceIcon,
} from '@/pages/supportShared';
import './support.css';

const OWNER_COLS = '130px 1fr 150px 190px 150px 90px';

/**
 * Panel del Dueño: gestiona los tickets de soporte/feedback de SU negocio. Lista en filas-tarjeta
 * (no tabla HTML), chips de tipo/estado, filtros y buscador, detalle con galería + contexto y
 * cambio de estado (solo OWNER). El backend acota por business_id del tenant.
 */
export function SupportPage() {
  const [type, setType] = useState<string>('');
  const [status, setStatus] = useState<string>('');
  const [q, setQ] = useState('');
  const [detailId, setDetailId] = useState<string | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();

  // Deep-link: /support?ticket=<publicId> abre el detalle directo (desde la campanita).
  useEffect(() => {
    const t = searchParams.get('ticket');
    if (t) setDetailId(t);
  }, [searchParams]);

  const closeDetail = () => {
    setDetailId(null);
    if (searchParams.has('ticket')) {
      searchParams.delete('ticket');
      setSearchParams(searchParams, { replace: true });
    }
  };

  const tickets = useQuery({
    queryKey: ['support', 'tickets', { type, status }],
    queryFn: async () => {
      const params: Record<string, string> = {};
      if (type) params.type = type;
      if (status) params.status = status;
      return (await api.get<TicketListItem[]>('/support/tickets', { params })).data;
    },
  });

  const list = tickets.data ?? [];
  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (!needle) return list;
    return list.filter((t) =>
      t.title.toLowerCase().includes(needle) || (t.reporterEmail ?? '').toLowerCase().includes(needle));
  }, [list, q]);

  const totals = useMemo(() => {
    const by: Record<string, number> = {};
    for (const t of list) by[t.status] = (by[t.status] ?? 0) + 1;
    return by;
  }, [list]);

  return (
    <div>
      <h1 className="page-title">Soporte</h1>
      <p className="page-sub">Reportes de error y peticiones de mejora de tu negocio</p>

      <InfoBanner icon={<LifeBuoy size={20} />} title="Buzón de soporte de tu negocio" tone="brand">
        Aquí llegan los reportes que tú y tu equipo envían con el botón de soporte. Cambia su estado
        conforme los atendemos.
      </InfoBanner>

      <div className="sup-toolbar">
        <div className="sup-search">
          <Search size={16} />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Buscar por título o quién reportó…" />
        </div>
        <div className="sup-filter-chips">
          <button className={`sup-filter ${type === '' ? 'is-active' : ''}`} onClick={() => setType('')}>Todos</button>
          <button className={`sup-filter ${type === 'ERROR' ? 'is-active' : ''}`} onClick={() => setType('ERROR')}>
            <Bug size={14} /> Errores
          </button>
          <button className={`sup-filter ${type === 'MEJORA' ? 'is-active' : ''}`} onClick={() => setType('MEJORA')}>
            <Lightbulb size={14} /> Mejoras
          </button>
        </div>
        <select className="sup-select" value={status} onChange={(e) => setStatus(e.target.value)}>
          {STATUS_FILTERS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
      </div>

      <div className="card sup-list-card">
        {tickets.isLoading ? (
          <p className="admin-plan-hint">Cargando…</p>
        ) : filtered.length === 0 ? (
          <div className="sup-empty">
            <div className="sup-empty-icon" aria-hidden><Inbox size={38} /></div>
            <strong>Aún no hay reportes</strong>
            <p>Usa el botón de soporte (abajo a la derecha) para reportar un error o pedir una mejora.</p>
          </div>
        ) : (
          <>
            <div className="sup-row sup-row-head" style={{ gridTemplateColumns: OWNER_COLS }}>
              <span>Tipo</span><span>Título</span><span>Estado</span>
              <span>Reportó</span><span>Fecha</span><span className="sup-num">Imágenes</span>
            </div>
            <motion.div variants={staggerContainer} initial="hidden" animate="visible">
              {filtered.map((t) => (
                <SupportRow key={t.publicId} t={t} cols={OWNER_COLS} onOpen={() => setDetailId(t.publicId)} />
              ))}
            </motion.div>
            <div className="sup-foot">
              {STATUS_FILTERS.filter((s) => s.value && totals[s.value]).map((s) => (
                <span key={s.value} className={`sup-chip ${STATUS_META[s.value as TicketStatus]?.cls ?? ''}`}>
                  {totals[s.value]} {STATUS_META[s.value as TicketStatus]?.label.toLowerCase()}
                </span>
              ))}
            </div>
          </>
        )}
      </div>

      <AnimatePresence>
        {detailId && (
          <SupportDetailModal publicId={detailId} resourceBase="/support/tickets" canChange onClose={closeDetail} />
        )}
      </AnimatePresence>
    </div>
  );
}

/** Fila-tarjeta compartida: el grid lo decide el contenedor para que cabecera y filas cuadren. */
export function SupportRow({ t, cols, onOpen, businessCell }:
  { t: TicketListItem; cols: string; onOpen: () => void; businessCell?: React.ReactNode }) {
  const tm = TYPE_META[t.type];
  const sm = STATUS_META[t.status];
  const dev = deviceIcon(t.device);
  const when = fmtDateTime(t.createdAt);
  return (
    <motion.button
      type="button"
      className="sup-row sup-row-item"
      style={{ gridTemplateColumns: cols }}
      variants={staggerItem}
      onClick={onOpen}
    >
      {businessCell}
      <span><span className={`sup-chip ${tm.cls}`}>{tm.icon}{tm.label}</span></span>
      <span className="sup-title-cell">{t.title}</span>
      <span><span className={`sup-chip ${sm.cls}`}>{sm.label}</span></span>
      <span className="sup-reporter">
        <span className="sup-dev" title={t.device}>{dev}</span>
        <span className="sup-reporter-email">{t.reporterEmail ?? t.reporterRole}</span>
      </span>
      <span className="sup-when sup-num">{when.date}<small>{when.time}</small></span>
      <span className="sup-num sup-imgcount"><ImageIcon size={13} /> {t.imageCount}</span>
    </motion.button>
  );
}

/**
 * Modal de detalle con contexto, galería y cambio de estado (compartido por ambas superficies).
 * `resourceBase` es la base REST ya con el segmento de tickets: '/support/tickets' para el dueño,
 * '/admin/support-tickets' para el super-admin.
 */
export function SupportDetailModal({ publicId, resourceBase, canChange, businessName, onClose }:
  { publicId: string; resourceBase: string; canChange: boolean; businessName?: string; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');

  const detail = useQuery({
    queryKey: ['support', 'detail', resourceBase, publicId],
    queryFn: async () => (await api.get<TicketView>(`${resourceBase}/${publicId}`)).data,
  });

  const changeStatus = useMutation({
    mutationFn: async (next: TicketStatus) =>
      (await api.patch<TicketView>(`${resourceBase}/${publicId}/status`, { status: next, note: note.trim() || null })).data,
    onSuccess: (data) => {
      queryClient.setQueryData(['support', 'detail', resourceBase, publicId], data);
      queryClient.invalidateQueries({ queryKey: ['support', 'tickets'] });
      queryClient.invalidateQueries({ queryKey: ['admin-support', 'tickets'] });
      setNote('');
      toast.success('Estado actualizado', `El ticket pasó a ${STATUS_META[data.status].label}.`);
    },
    onError: () => toast.error('No se pudo cambiar el estado', 'Revisa que la transición sea válida.'),
  });

  // ---- Conversación bidireccional del ticket (Pieza 1) ----
  const thread = useQuery({
    queryKey: ['support', 'thread', resourceBase, publicId],
    queryFn: async () =>
      (await api.get<MessageView[]>(`${resourceBase}/${publicId}/messages`)).data,
  });

  const [msgBody, setMsgBody] = useState('');
  const postMsg = useMutation({
    mutationFn: async (body: string) =>
      (await api.post<MessageView>(`${resourceBase}/${publicId}/messages`, { body })).data,
    onSuccess: () => {
      setMsgBody('');
      // Solo el hilo y las notificaciones se invalidan: no refrescamos la lista de tickets
      // (un mensaje no cambia el estado ni el orden de la tabla).
      queryClient.invalidateQueries({ queryKey: ['support', 'thread', resourceBase, publicId] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      toast.success('Enviado', 'Tu mensaje fue publicado.');
    },
    onError: () => toast.error('Error', 'No se pudo enviar el mensaje.'),
  });

  const t = detail.data;
  const sm = t ? STATUS_META[t.status] : null;
  const tm = t ? TYPE_META[t.type] : null;
  const transitions = t ? OWNER_TRANSITIONS[t.status] ?? [] : [];

  return (
    <motion.div className="modal-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={quick}>
      <motion.div className="modal-card modal-wide sup-detail" onClick={(e) => e.stopPropagation()}
        variants={popIn} initial="hidden" animate="visible" exit="exit">
        <div className="modal-head">
          <h3>Detalle del reporte</h3>
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        </div>
        <div className="modal-body">
          {detail.isLoading || !t ? (
            <p className="admin-plan-hint">Cargando…</p>
          ) : (
            <>
              <div className="sup-detail-head">
                <span className={`sup-chip ${tm!.cls}`}>{tm!.icon}{tm!.label}</span>
                <span className={`sup-chip ${sm!.cls}`}>{sm!.label}</span>
                {businessName && <span className="sup-detail-biz">{businessName}</span>}
              </div>
              <h4 className="sup-detail-title">{t.title}</h4>
              <p className="sup-detail-desc">{t.description}</p>

              {t.images.length > 0 && (
                <div className="sup-gallery">
                  {t.images.map((img) => (
                    <a key={img.publicId} href={img.url} target="_blank" rel="noreferrer" className="sup-gallery-item">
                      <img src={img.url} alt="Adjunto del reporte" loading="lazy" />
                    </a>
                  ))}
                </div>
              )}

              <div className="sup-ctx">
                <CtxItem label="Pantalla" value={t.screenPath ?? '—'} />
                <CtxItem label="Dispositivo" value={t.device} />
                <CtxItem label="Versión" value={t.appVersion ?? '—'} />
                <CtxItem label="Reportó" value={`${t.reporterEmail ?? t.reporterUserId} · ${t.reporterRole}`} />
                <CtxItem label="Creado" value={fmtDateTime(t.createdAt).full} />
                {t.resolvedAt && <CtxItem label="Resuelto" value={`${fmtDateTime(t.resolvedAt).full}${t.resolvedBy ? ` · ${t.resolvedBy}` : ''}`} />}
              </div>

              {t.userAgent && <p className="sup-ua" title={t.userAgent}>{t.userAgent}</p>}
              {t.resolutionNote && (
                <div className="sup-resnote"><strong>Nota:</strong> {t.resolutionNote}</div>
              )}

              <div className="sup-thread">
                <h5 className="sup-thread-title">Conversación</h5>
                {thread.isLoading ? <p className="admin-plan-hint">Cargando…</p> :
                  (thread.data ?? []).length === 0 ? (
                    <div className="sup-thread-empty">
                      <p>Aún no hay respuestas. Escribe el primer mensaje para continuar la conversación.</p>
                    </div>
                  ) : (
                    <div className="sup-bubbles">
                      {(thread.data ?? []).map((msg) => {
                        const meta = AUTHOR_META[msg.authorType];
                        return (
                          <div key={msg.publicId} className={`sup-bubble sup-bubble-${meta.side} ${meta.cls}`}>
                            <span className="sup-bubble-author">{meta.label}
                              {msg.authorType === 'REPORTER' && msg.authorEmail &&
                                <small className="sup-bubble-email"> · {msg.authorEmail}</small>}
                              {msg.authorType === 'SUPER_ADMIN' && msg.viaEmail &&
                                <span className="sup-chip sup-via-email">📧 por correo</span>}
                            </span>
                            <p className="sup-bubble-body">{msg.body}</p>
                            <span className="sup-bubble-time sup-num">{fmtDateTime(msg.createdAt).full}</span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                <div className="sup-reply-box">
                  <textarea
                    className="sup-reply-input"
                    placeholder="Escribe tu respuesta…"
                    value={msgBody}
                    onChange={(e) => setMsgBody(e.target.value)}
                    maxLength={4000}
                    rows={3}
                  />
                  <div className="sup-reply-footer">
                    <small className={msgBody.length >= 3600 ? 'sup-reply-warn' : 'sup-reply-count'}>
                      {msgBody.length}/4000
                    </small>
                    <motion.button
                      className="sup-reply-send"
                      disabled={postMsg.isPending || !msgBody.trim()}
                      onClick={() => postMsg.mutate(msgBody.trim())}
                      whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                      <Send size={15} /> Enviar
                    </motion.button>
                  </div>
                </div>
              </div>

              {canChange && transitions.length > 0 && (
                <div className="sup-actions-box">
                  <label className="field sup-field">
                    <span>Nota (opcional)</span>
                    <input value={note} onChange={(e) => setNote(e.target.value)}
                      placeholder="Agrega una nota al cambiar el estado" maxLength={2000} />
                  </label>
                  <div className="sup-transitions">
                    {transitions.map((next) => (
                      <motion.button key={next} className={`sup-trans-btn ${STATUS_META[next].cls}`}
                        disabled={changeStatus.isPending}
                        onClick={() => changeStatus.mutate(next)}
                        whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                        Marcar {STATUS_META[next].label}
                      </motion.button>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </motion.div>
    </motion.div>
  );
}

function CtxItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="sup-ctx-item">
      <span className="sup-ctx-label">{label}</span>
      <span className="sup-ctx-value">{value}</span>
    </div>
  );
}
