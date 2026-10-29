import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { CalendarCheck, Croissant, ClipboardList } from 'lucide-react';
import { api } from '@/lib/api';
import { InfoBanner } from '@/components/InfoBanner';
import { fadeInUp } from '@/lib/motion';
import { fmtDate } from '@/lib/saleVisuals';
import { storedBranchId } from '@/lib/cashierBranch';
import '@/pages/dashboard.css';
import '@/pages/baker.css';

const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

interface Branch { id: number; name: string; active: boolean; }
interface BatchRow {
  batchId: number;
  folio: string;
  batchDate: string;
  createdAt: string | null;
  totalOutput: number;
  outputs: { productName: string; quantity: number }[];
}

/**
 * "Mis tandas de hoy" del panadero: filas-tarjeta de las tandas que capturó hoy
 * (folio T-, hora, Σ panes). El filtro por actor y día MX lo hace el backend.
 */
export function BakerMyBatchesPage() {
  const branchId = storedBranchId() || 1;
  const branches = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await api.get<Branch[]>('/branches')).data,
  });
  const resolvedBranch = useMemo(() => {
    const active = (branches.data ?? []).filter((b) => b.active);
    if (active.some((b) => b.id === branchId)) return branchId;
    return active[0]?.id ?? branchId;
  }, [branches.data, branchId]);

  const batches = useQuery({
    queryKey: ['baker', 'mine', resolvedBranch],
    queryFn: async () => (await api.get<BatchRow[]>('/bakery/batches/mine', {
      params: { branchId: resolvedBranch },
    })).data,
  });

  return (
    <div className="bk-wrap">
      <h1 className="page-title">Mis tandas de hoy</h1>
      <InfoBanner icon={<CalendarCheck size={18} />} tone="brand" title="Lo que hiciste hoy">
        Aquí ves las tandas que registraste hoy, con su folio y cuánto pan salió.
      </InfoBanner>

      <motion.div className="card" style={{ marginTop: 'var(--space-3)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Croissant size={18} /> Tandas de hoy</h3>
        <div className="bk-list">
          {(batches.data ?? []).map((b) => {
            const when = fmtDate(b.createdAt);
            return (
              <div className="bk-row" key={b.batchId} style={{ gridTemplateColumns: 'auto minmax(0,1fr) auto' }}>
                <span className="bk-chip is-violet">{b.folio}</span>
                <div className="bk-article-text">
                  <div className="bk-article-name">
                    {b.outputs.map((o) => `${o.productName} (${int(o.quantity)})`).join(', ') || 'Tanda'}
                  </div>
                  <div className="bk-article-unit">{when.time}</div>
                </div>
                <span className="bk-chip is-green">{int(b.totalOutput)} pz</span>
              </div>
            );
          })}
          {!batches.isLoading && (batches.data ?? []).length === 0 && (
            <div className="bk-empty">
              <ClipboardList size={28} strokeWidth={1.5} />
              <strong>Aún no registras tandas hoy</strong>
              <span>Ve a "Nueva tanda" para registrar tu primera hornada del día.</span>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
}
