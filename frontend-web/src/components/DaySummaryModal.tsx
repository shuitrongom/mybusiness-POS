import type { ReactNode } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Wallet, Banknote, CreditCard, Landmark, Ticket as TicketIcon, Receipt,
  Package, ArrowDownCircle, ArrowUpCircle, Scale, Check, ClipboardCheck,
  Layers, Sparkles,
} from 'lucide-react';
import { InfoBanner } from '@/components/InfoBanner';
import '@/pages/pos.css';
import '@/pages/dashboard.css';

/**
 * Resumen del día de la cajera tras cerrar su caja (proveniente de GET /shifts/my-day).
 * Es la MISMA fuente que consume el dashboard del cajero; aquí solo se presenta en un modal
 * premium. Los campos del split NORMAL/ESPECIAL son opcionales porque no todos los giros lo usan.
 */
export interface DaySummary {
  registerName: string | null;
  openingFloat: number;
  cashSales: number; cardSales: number; transferSales: number; voucherSales: number;
  creditSales: number; cashIn: number; cashOut: number;
  expectedCash: number; totalSales: number; salesCount: number; units: number; averageTicket: number;
  countedCash?: number | null;
  difference?: number | null;
  normalSalesCount?: number | null;
  normalSalesTotal?: number | null;
  specialSalesCount?: number | null;
  specialSalesTotal?: number | null;
}

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(Math.round(n || 0));

/** Modo con el que se cerró: ajusta el texto del banner de contexto (relevo vs fin del día). */
type CloseMode = 'TURNO' | 'FIN_DIA';

/**
 * Modal de PRESENTACIÓN del resumen del día de la cajera al cerrar caja. No imprime nada
 * (ahorro de papel): solo muestra la actividad del turno siguiendo el lenguaje visual del
 * dashboard del cajero (héroe del total, KPIs tabular-nums, dinero con $, chips de color). El
 * botón "Entendido" ejecuta {@code onDone}, que bloquea el POS por hoy.
 */
export function DaySummaryModal({ summary, mode, onDone }:
  { summary: DaySummary; mode: CloseMode; onDone: () => void }) {
  const diff = summary.difference ?? 0;
  const counted = summary.countedCash ?? 0;
  const hasSplit = (summary.normalSalesCount ?? 0) > 0 || (summary.specialSalesCount ?? 0) > 0;

  return (
    <AnimatePresence>
      <motion.div className="pos-modal-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
        <motion.div className="pos-modal day-summary-modal" onClick={(e) => e.stopPropagation()}
          initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.97 }}>
          <div className="pos-modal-head">
            <h3 style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <ClipboardCheck size={18} /> Resumen de tu día
            </h3>
          </div>

          <div className="day-summary-body">
            <InfoBanner
              icon={<Sparkles size={18} />}
              title={mode === 'FIN_DIA' ? 'Fin del día cerrado' : 'Turno cerrado'}
              tone={mode === 'FIN_DIA' ? 'amber' : 'sky'}
            >
              {mode === 'FIN_DIA'
                ? 'Tu corte quedó registrado y el pan sobrante pasó a merma. Este es el resumen de tu jornada; no se imprime para ahorrar papel.'
                : 'Tu corte quedó registrado y el pan sobrante sigue vendible para la siguiente cajera. Este es el resumen de tu turno; no se imprime para ahorrar papel.'}
            </InfoBanner>

            {/* Héroe: total vendido + KPIs de apoyo */}
            <div className="day-summary-hero">
              <div className="hero-metric">
                <div className="hero-metric-top">
                  <span className="hero-metric-label">Vendido en tu {mode === 'FIN_DIA' ? 'día' : 'turno'}</span>
                </div>
                <div className="hero-metric-value num">{money(summary.totalSales)}</div>
                <div className="hero-metric-foot">
                  <span className="num">{int(summary.salesCount)}</span> ventas · ticket promedio{' '}
                  <span className="num">{money(summary.averageTicket)}</span> · <span className="num">{int(summary.units)}</span> unidades
                </div>
                <div className="hero-glow" />
              </div>

              <div className="kpi-column">
                <Kpi icon={<Banknote size={18} />} tint="green" label="Efectivo esperado" value={money(summary.expectedCash)} hint="fondo + efectivo − retiros" />
                <Kpi icon={<Receipt size={18} />} tint="blue" label="Ventas" value={int(summary.salesCount)} />
                <Kpi icon={<Package size={18} />} tint="amber" label="Unidades" value={int(summary.units)} />
                <Kpi icon={<CreditCard size={18} />} tint="violet" label="Ticket promedio" value={money(summary.averageTicket)} />
              </div>
            </div>

            {/* Corte final: esperado vs contado y diferencia */}
            <div className="day-summary-card">
              <div className="sec-title"><Scale size={18} /><h3>Tu corte final</h3></div>
              <div className="cash-cut-grid">
                <Kpi icon={<Banknote size={18} />} tint="green" label="Efectivo esperado" value={money(summary.expectedCash)} />
                <Kpi icon={<Wallet size={18} />} tint="blue" label="Efectivo contado" value={money(counted)} />
                <Kpi
                  icon={<Scale size={18} />}
                  tint={diff === 0 ? 'green' : 'amber'}
                  label="Diferencia"
                  value={money(diff)}
                  hint={diff === 0 ? 'cuadró exacto' : (diff > 0 ? 'sobrante' : 'faltante')}
                />
              </div>
            </div>

            {/* Desglose por método de pago */}
            <div className="day-summary-card">
              <div className="sec-title"><Wallet size={18} /><h3>Cómo cobraste hoy</h3></div>
              <div className="cash-methods">
                <MethodRow icon={<Banknote size={16} />} label="Efectivo" value={summary.cashSales} tint="green" />
                <MethodRow icon={<CreditCard size={16} />} label="Tarjeta" value={summary.cardSales} tint="blue" />
                <MethodRow icon={<Landmark size={16} />} label="Transferencia" value={summary.transferSales} tint="violet" />
                <MethodRow icon={<TicketIcon size={16} />} label="Vale" value={summary.voucherSales} tint="amber" />
                {summary.creditSales > 0 && (
                  <MethodRow icon={<Receipt size={16} />} label="A crédito" value={summary.creditSales} tint="amber" />
                )}
              </div>
              <div className="cash-moves">
                <div className="cash-move"><span><ArrowDownCircle size={15} /> Entradas de efectivo</span><strong className="num">{money(summary.cashIn)}</strong></div>
                <div className="cash-move"><span><ArrowUpCircle size={15} /> Salidas / retiros</span><strong className="num">-{money(summary.cashOut)}</strong></div>
              </div>
            </div>

            {/* Split NORMAL / ESPECIAL (solo si el giro lo usa) */}
            {hasSplit && (
              <div className="day-summary-card">
                <div className="sec-title"><Layers size={18} /><h3>Ventas por tipo</h3></div>
                <div className="cash-methods">
                  <MethodRow
                    icon={<Receipt size={16} />}
                    label={`Normal (${int(summary.normalSalesCount ?? 0)})`}
                    value={summary.normalSalesTotal ?? 0}
                    tint="blue"
                  />
                  <MethodRow
                    icon={<Sparkles size={16} />}
                    label={`Especial (${int(summary.specialSalesCount ?? 0)})`}
                    value={summary.specialSalesTotal ?? 0}
                    tint="violet"
                  />
                </div>
              </div>
            )}
          </div>

          <div className="pos-modal-actions">
            <button className="pos-confirm" onClick={onDone}><Check size={15} /> Entendido</button>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}

function Kpi({ icon, label, value, tint, hint }: { icon: ReactNode; label: string; value: string; tint: string; hint?: string }) {
  return (
    <div className="kpi-card">
      <span className={`kpi-icon kpi-${tint}`}>{icon}</span>
      <div className="kpi-body">
        <span className="kpi-label">{label}</span>
        <span className="kpi-value num">{value}</span>
        {hint && <span className="kpi-hint">{hint}</span>}
      </div>
    </div>
  );
}

function MethodRow({ icon, label, value, tint }: { icon: ReactNode; label: string; value: number; tint: string }) {
  return (
    <div className="cash-method">
      <span className={`kpi-icon kpi-${tint}`}>{icon}</span>
      <span className="cash-method-label">{label}</span>
      <strong className="cash-method-value num">{money(value)}</strong>
    </div>
  );
}
