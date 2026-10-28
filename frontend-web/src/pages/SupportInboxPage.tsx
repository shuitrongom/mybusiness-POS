import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'motion/react';
import { LifeBuoy, Bug, Lightbulb, Search, Inbox, Image as ImageIcon } from 'lucide-react';
import { api } from '@/lib/api';
import { InfoBanner } from '@/components/InfoBanner';
import { avatarColor, avatarInitials } from '@/lib/saleVisuals';
import { staggerContainer, staggerItem } from '@/lib/motion';
import { SupportDetailModal } from '@/pages/SupportPage';
import {
  type TicketListItem, type TicketStatus,
  TYPE_META, STATUS_META, STATUS_FILTERS, fmtDateTime, deviceIcon,
} from '@/pages/supportShared';
import './support.css';

const INBOX_COLS = '210px 120px 1fr 150px 170px 140px 80px';

/** Negocio (para mapear businessId -> nombre y poblar el filtro). */
interface Business {
  id: number;
  name: string;
}

/**
 * Bandeja global del Super Admin: ve los tickets de TODOS los negocios. Mismas filas-tarjeta y
 * chips que el panel del dueño, más columna/filtro por negocio (avatar con color estable derivado
 * del nombre, reusando el helper de la app) y buscador global. Consume /api/v1/admin/support-tickets.
 */
export function SupportInboxPage() {
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [businessId, setBusinessId] = useState('');
  const [q, setQ] = useState('');
  const [detailId, setDetailId] = useState<string | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();

  // Deep-link: /admin-support?ticket=<publicId> abre el detalle directo (desde la campanita).
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

  const businesses = useQuery({
    queryKey: ['admin', 'businesses'],
    queryFn: async () => (await api.get<Business[]>('/admin/businesses')).data,
  });

  const tickets = useQuery({
    queryKey: ['admin-support', 'tickets', { type, status, businessId }],
    queryFn: async () => {
      const params: Record<string, string> = {};
      if (type) params.type = type;
      if (status) params.status = status;
      if (businessId) params.businessId = businessId;
      return (await api.get<TicketListItem[]>('/admin/support-tickets', { params })).data;
    },
  });

  const bizName = (id: number | null) =>
    businesses.data?.find((b) => b.id === id)?.name ?? (id ? `Negocio #${id}` : 'Sin negocio');

  const list = tickets.data ?? [];
  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (!needle) return list;
    return list.filter((t) =>
      t.title.toLowerCase().includes(needle)
      || (t.reporterEmail ?? '').toLowerCase().includes(needle)
      || bizName(t.businessId).toLowerCase().includes(needle));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [list, q, businesses.data]);

  const totals = useMemo(() => {
    const by: Record<string, number> = {};
    for (const t of list) by[t.status] = (by[t.status] ?? 0) + 1;
    return by;
  }, [list]);

  const detailBiz = filtered.find((t) => t.publicId === detailId)?.businessId ?? null;

  return (
    <div>
      <h1 className="page-title">Soporte</h1>
      <p className="page-sub">Reportes de todos los negocios de la plataforma</p>

      <InfoBanner icon={<LifeBuoy size={20} />} title="Bandeja global de soporte" tone="violet">
        Aquí ves los reportes de error y peticiones de mejora de todos los negocios. Filtra por
        negocio, tipo o estado y gestiona cada ticket.
      </InfoBanner>

      <div className="sup-toolbar">
        <div className="sup-search">
          <Search size={16} />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Buscar por negocio, título o quién reportó…" />
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
        <select className="sup-select" value={businessId} onChange={(e) => setBusinessId(e.target.value)}>
          <option value="">Todos los negocios</option>
          {(businesses.data ?? []).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
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
            <strong>Sin tickets de soporte</strong>
            <p>Cuando un negocio reporte algo desde el sistema, aparecerá aquí.</p>
          </div>
        ) : (
          <>
            <div className="sup-row sup-row-head" style={{ gridTemplateColumns: INBOX_COLS }}>
              <span>Negocio</span><span>Tipo</span><span>Título</span><span>Estado</span>
              <span>Reportó</span><span>Fecha</span><span className="sup-num">Imágenes</span>
            </div>
            <motion.div variants={staggerContainer} initial="hidden" animate="visible">
              {filtered.map((t) => {
                const tm = TYPE_META[t.type];
                const sm = STATUS_META[t.status];
                const name = bizName(t.businessId);
                const when = fmtDateTime(t.createdAt);
                return (
                  <motion.button
                    key={t.publicId}
                    type="button"
                    className="sup-row sup-row-item"
                    style={{ gridTemplateColumns: INBOX_COLS }}
                    variants={staggerItem}
                    onClick={() => setDetailId(t.publicId)}
                  >
                    <span className="sup-biz">
                      <span className="sup-biz-avatar" style={{ background: avatarColor(name) }}>
                        {avatarInitials(name)}
                      </span>
                      <span className="sup-biz-name">{name}</span>
                    </span>
                    <span><span className={`sup-chip ${tm.cls}`}>{tm.icon}{tm.label}</span></span>
                    <span className="sup-title-cell">{t.title}</span>
                    <span><span className={`sup-chip ${sm.cls}`}>{sm.label}</span></span>
                    <span className="sup-reporter">
                      <span className="sup-dev" title={t.device}>{deviceIcon(t.device)}</span>
                      <span className="sup-reporter-email">{t.reporterEmail ?? t.reporterRole}</span>
                    </span>
                    <span className="sup-when sup-num">{when.date}<small>{when.time}</small></span>
                    <span className="sup-num sup-imgcount"><ImageIcon size={13} /> {t.imageCount}</span>
                  </motion.button>
                );
              })}
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
          <SupportDetailModal
            publicId={detailId}
            resourceBase="/admin/support-tickets"
            canChange
            businessName={bizName(detailBiz)}
            onClose={closeDetail}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
