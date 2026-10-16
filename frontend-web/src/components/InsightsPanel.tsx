import { motion } from 'motion/react';
import {
  AlertTriangle, CheckCircle2, Info, Sparkles, type LucideIcon,
} from 'lucide-react';
import { staggerContainer, staggerItem } from '@/lib/motion';
import './insights-panel.css';

/** Severidad de un insight tal como la emite el InsightEngine del backend. */
export type InsightSeverity = 'POSITIVO' | 'ALERTA' | 'INFO';

/** Insight generado por IA (RuleBasedInsightEngine hoy; LlmInsightEngine en el futuro). */
export interface Insight {
  tipo: string;
  severidad: InsightSeverity;
  titulo: string;
  mensaje: string;
  valor: string | null;
}

const SEV_META: Record<InsightSeverity, { icon: LucideIcon; cls: string }> = {
  POSITIVO: { icon: CheckCircle2, cls: 'insight-sev-positivo' },
  ALERTA: { icon: AlertTriangle, cls: 'insight-sev-alerta' },
  INFO: { icon: Info, cls: 'insight-sev-info' },
};

/**
 * Panel "Análisis inteligente": presenta los insights del negocio o de la plataforma como tarjetas
 * con icono y color por severidad y un badge "IA". Reutilizable por el dueño y el Super Admin, de
 * modo que ambos ven el mismo lenguaje visual. Si no hay insights muestra un estado vacío elegante.
 */
export function InsightsPanel({ insights }: { insights: Insight[] }) {
  return (
    <section className="insights-panel">
      <div className="insights-head">
        <span className="insights-ia-icon"><Sparkles size={16} /></span>
        <h3 className="insights-title">Análisis inteligente</h3>
        <span className="insight-badge-ia">IA</span>
      </div>

      {insights.length === 0 ? (
        <div className="insights-empty">
          <Sparkles size={22} />
          <p>Aún no hay suficientes datos para generar análisis. Registra ventas y vuelve pronto.</p>
        </div>
      ) : (
        <motion.div
          className="insights-grid"
          variants={staggerContainer}
          initial="hidden"
          animate="visible"
        >
          {insights.map((it, i) => {
            const meta = SEV_META[it.severidad] ?? SEV_META.INFO;
            const Icon = meta.icon;
            return (
              <motion.article
                key={`${it.tipo}-${i}`}
                className={`insight-card ${meta.cls}`}
                variants={staggerItem}
              >
                <span className="insight-icon"><Icon size={18} /></span>
                <div className="insight-body">
                  <h4 className="insight-card-title">{it.titulo}</h4>
                  <p className="insight-card-msg">{it.mensaje}</p>
                  {it.valor && <span className="insight-card-value">{it.valor}</span>}
                </div>
              </motion.article>
            );
          })}
        </motion.div>
      )}
    </section>
  );
}
