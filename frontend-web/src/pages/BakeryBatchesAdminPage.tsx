import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  BarChart3, ChevronDown, TriangleAlert, Info, Croissant,
} from 'lucide-react';
import { api } from '@/lib/api';
import { InfoBanner } from '@/components/InfoBanner';
import { fadeInUp } from '@/lib/motion';
import { fmtDate } from '@/lib/saleVisuals';
import '@/pages/dashboard.css';
import '@/pages/baker.css';
import '@/pages/admin.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);
const dec = (n: number | null) =>
  n == null ? '—' : new Intl.NumberFormat('es-MX', { maximumFractionDigits: 2 }).format(n);

interface BatchRow {
  batchId: number;
  folio: string;
  batchDate: string;
  branchName: string | null;
  createdBy: string | null;
  createdRole: string | null;
  inputCost: number;
  outputValue: number;
  valueOverInputs: number;
  totalInput: number;
  totalOutput: number;
  hasZeroCostInput: boolean;
  createdAt: string | null;
  yieldTotal: number | null;
  movingAvg: number | null;
  deviation: 'OK' | 'HIGH' | 'LOW';
}
interface BatchDetail extends BatchRow {
  notes: string | null;
  inputs: { name: string; quantity: number; unit: string; unitCost: number; lineCost: number }[];
  outputs: { productName: string; quantity: number; entryPrice: number; lineValue: number }[];
}
interface Anomaly {
  category: 'BACKUP_PRODUCTION' | 'MANUAL_ADJUSTMENT';
  folio?: string;
  createdBy?: string;
  createdRole?: string;
  totalQty?: number;
  productName?: string;
  quantity?: number;
  actor?: string;
  reason?: string;
}

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function monthStartIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
}

/**
 * Panel admin de tandas: consumo, producción, rendimiento, discrepancias y la métrica
 * "Valor del pan − Costo de insumos" (NO margen neto). Filas-tarjeta con detalle
 * expandible; discrepancias en DOS secciones (respaldo gris / ajuste manual ámbar).
 */
export function BakeryBatchesAdminPage() {
  const [from, setFrom] = useState(monthStartIso());
  const [to, setTo] = useState(todayIso());
  const [expanded, setExpanded] = useState<number | null>(null);

  const batches = useQuery({
    queryKey: ['bakery-batches', from, to],
    queryFn: async () => (await api.get<BatchRow[]>('/bakery/batches', { params: { from, to, limit: 200 } })).data,
  });
  const anomalies = useQuery({
    queryKey: ['bakery-batches', 'anomalies', from, to],
    queryFn: async () => (await api.get<Anomaly[]>('/bakery/batches/anomalies', { params: { from, to } })).data,
  });
  const detail = useQuery({
    queryKey: ['bakery-batch-detail', expanded],
    enabled: expanded != null,
    queryFn: async () => (await api.get<BatchDetail>(`/bakery/batches/${expanded}`)).data,
  });

  const totals = useMemo(() => {
    const rows = batches.data ?? [];
    return {
      inputCost: rows.reduce((s, r) => s + (r.inputCost || 0), 0),
      outputValue: rows.reduce((s, r) => s + (r.outputValue || 0), 0),
      voi: rows.reduce((s, r) => s + (r.valueOverInputs || 0), 0),
    };
  }, [batches.data]);

  const backup = (anomalies.data ?? []).filter((a) => a.category === 'BACKUP_PRODUCTION');
  const manual = (anomalies.data ?? []).filter((a) => a.category === 'MANUAL_ADJUSTMENT');

  return (
    <div>
      <h1 className="page-title">Tandas y rendimiento</h1>
      <InfoBanner icon={<BarChart3 size={18} />} tone="violet" title="Análisis de tus hornadas">
        Consumo de insumos, producción de pan, rendimiento por tanda y el valor del pan por encima de
        sus insumos. Es un indicador de gestión: no incluye mano de obra ni energía; no es margen neto.
      </InfoBanner>

      <motion.div className="card" style={{ marginTop: 'var(--space-3)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <div className="admin-grid">
          <label className="field"><span>Desde</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="field"><span>Hasta</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Croissant size={18} /> Tandas ({batches.data?.length ?? 0})</h3>
        <div className="bk-list">
          {(batches.data ?? []).map((b) => {
            const when = fmtDate(b.createdAt);
            const isOpen = expanded === b.batchId;
            const devChip = b.deviation === 'OK' ? null
              : <span className="bk-chip is-amber">Rendimiento {b.deviation === 'HIGH' ? 'alto' : 'bajo'}</span>;
            // Mini-barra: rendimiento relativo al promedio móvil (tope visual 150%).
            const pct = b.movingAvg && b.movingAvg > 0 && b.yieldTotal != null
              ? Math.min(100, (b.yieldTotal / b.movingAvg) * 66) : 50;
            return (
              <div key={b.batchId}>
                <div className="bk-row" style={{ gridTemplateColumns: 'auto minmax(0,1fr) auto auto 44px', cursor: 'pointer' }}
                  onClick={() => setExpanded(isOpen ? null : b.batchId)}>
                  <span className="bk-chip is-violet">{b.folio}</span>
                  <div className="bk-article-text">
                    <div className="bk-article-name">{b.createdBy ?? '—'}{b.branchName ? ` · ${b.branchName}` : ''}</div>
                    <div className="bk-article-unit">
                      {when.date} · {int(b.totalInput)} insumo → {int(b.totalOutput)} pz
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <span className={`bk-chip ${b.hasZeroCostInput ? 'is-amber' : 'is-green'}`} title="Valor del pan − Costo de insumos">
                      {money(b.valueOverInputs)}
                    </span>
                    {b.hasZeroCostInput && <div className="bk-article-unit">no confiable</div>}
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    {devChip}
                    <div className="bk-bar" style={{ width: 90, marginTop: 4 }}><span style={{ width: `${pct}%` }} /></div>
                  </div>
                  <ChevronDown size={16} style={{ transform: isOpen ? 'rotate(180deg)' : 'none', transition: '.15s' }} />
                </div>

                {isOpen && (() => {
                  const d = detail.data && detail.data.batchId === b.batchId ? detail.data : null;
                  return (
                    <div className="card" style={{ margin: '6px 0', background: 'var(--surface-2)' }}>
                      {!d ? (
                        <p className="muted">Cargando detalle…</p>
                      ) : (
                        <>
                          <div className="bk-list">
                            <div className="bk-row-head" style={{ gridTemplateColumns: 'minmax(0,1fr) 110px 120px' }}>
                              <span>Insumo</span><span className="bk-h-right">Cantidad</span><span className="bk-h-right">Costo</span>
                            </div>
                            {d.inputs.map((i, idx) => (
                              <div className="bk-row" key={`i-${idx}`} style={{ gridTemplateColumns: 'minmax(0,1fr) 110px 120px' }}>
                                <span className="bk-article-name">{i.name}</span>
                                <span className="bk-h-right bk-num">{int(i.quantity)} {i.unit}</span>
                                <span className="bk-h-right bk-num">{money(i.lineCost)}</span>
                              </div>
                            ))}
                            <div className="bk-row-head" style={{ gridTemplateColumns: 'minmax(0,1fr) 110px 120px' }}>
                              <span>Pan</span><span className="bk-h-right">Cantidad</span><span className="bk-h-right">Valor</span>
                            </div>
                            {d.outputs.map((o, idx) => (
                              <div className="bk-row" key={`o-${idx}`} style={{ gridTemplateColumns: 'minmax(0,1fr) 110px 120px' }}>
                                <span className="bk-article-name">{o.productName}</span>
                                <span className="bk-h-right bk-num">{int(o.quantity)} pz</span>
                                <span className="bk-h-right bk-num">{money(o.lineValue)}</span>
                              </div>
                            ))}
                          </div>
                          <div className="bk-foot">
                            <div className="bk-foot-row"><span>Rendimiento (panes por insumo)</span><span className="bk-num">{dec(d.yieldTotal)}</span></div>
                            <div className="bk-foot-row"><span>Promedio histórico</span><span className="bk-num">{dec(d.movingAvg)}</span></div>
                            <div className="bk-foot-row"><span>Valor del pan − Costo de insumos</span><strong className="bk-num">{money(d.valueOverInputs)}</strong></div>
                          </div>
                        </>
                      )}
                    </div>
                  );
                })()}
              </div>
            );
          })}
          {!batches.isLoading && (batches.data ?? []).length === 0 && (
            <div className="bk-empty">
              <Croissant size={28} strokeWidth={1.5} />
              <strong>No hay tandas en el periodo</strong>
              <span>Cuando el panadero registre hornadas, aparecerán aquí con su rendimiento.</span>
            </div>
          )}
        </div>

        {(batches.data ?? []).length > 0 && (
          <div className="bk-foot">
            <div className="bk-foot-row"><span>Σ costo de insumos</span><span className="bk-num">{money(totals.inputCost)}</span></div>
            <div className="bk-foot-row"><span>Σ valor del pan</span><span className="bk-num">{money(totals.outputValue)}</span></div>
            <div className="bk-foot-row"><span>Σ Valor del pan − Costo de insumos</span><strong className="bk-num">{money(totals.voi)}</strong></div>
            <p className="muted" style={{ margin: '6px 0 0' }}>No incluye mano de obra, energía ni otros costos; no es margen neto.</p>
          </div>
        )}
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><TriangleAlert size={18} /> Discrepancias</h3>

        <h4 className="admin-sub" style={{ marginTop: 'var(--space-2)' }}>
          <Info size={14} style={{ verticalAlign: '-2px' }} /> Producción sin tanda (respaldo)
        </h4>
        <div className="bk-list">
          {backup.map((a, idx) => (
            <div className="bk-row" key={`b-${idx}`} style={{ gridTemplateColumns: 'auto minmax(0,1fr) auto' }}>
              <span className="bk-chip is-gray">{a.folio}</span>
              <div className="bk-article-text">
                <div className="bk-article-name">{a.createdBy ?? '—'}{a.createdRole ? ` · ${a.createdRole}` : ''}</div>
                <div className="bk-article-unit">{a.reason}</div>
              </div>
              <span className="bk-chip is-gray">{int(a.totalQty ?? 0)} pz</span>
            </div>
          ))}
          {backup.length === 0 && (
            <div className="bk-empty">
              <Info size={24} strokeWidth={1.5} />
              <strong>Sin producción de respaldo</strong>
              <span>Toda la producción del periodo vino de una tanda del panadero.</span>
            </div>
          )}
        </div>

        <h4 className="admin-sub" style={{ marginTop: 'var(--space-4)' }}>
          <TriangleAlert size={14} style={{ verticalAlign: '-2px' }} /> Ajuste/entrada manual de pan
        </h4>
        <div className="bk-list">
          {manual.map((a, idx) => (
            <div className="bk-row" key={`m-${idx}`} style={{ gridTemplateColumns: 'minmax(0,1fr) auto auto' }}>
              <div className="bk-article-text">
                <div className="bk-article-name">{a.productName ?? '—'}</div>
                <div className="bk-article-unit">{a.actor ?? '—'} · {a.reason}</div>
              </div>
              <span className="bk-chip is-amber">{int(a.quantity ?? 0)} pz</span>
            </div>
          ))}
          {manual.length === 0 && (
            <div className="bk-empty">
              <TriangleAlert size={24} strokeWidth={1.5} />
              <strong>Sin ajustes manuales de pan</strong>
              <span>No hay entradas/ajustes de pan fuera de producción o venta en el periodo.</span>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
}
