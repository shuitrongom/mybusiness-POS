import { useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { ArrowLeft } from 'lucide-react';
import type { ReactNode } from 'react';
import { staggerContainer, staggerItem } from '@/lib/motion';
import './module-landing.css';

/** Una opción (sub-módulo) dentro de la página de un módulo. */
export interface ModuleOption {
  to: string;
  label: string;
  sub: string;
  icon: ReactNode;
  from: string;
  to2: string;
}

/** Un indicador (KPI) del resumen del módulo. */
export interface ModuleStat {
  label: string;
  value: string;
  icon: ReactNode;
  tone?: 'accent' | 'success' | 'warning' | 'danger';
}

interface ModuleLandingProps {
  title: string;
  subtitle: string;
  accentFrom: string;
  accentTo: string;
  stats?: ModuleStat[];
  options: ModuleOption[];
}

/**
 * Página "landing" de un módulo: un encabezado con degradado del módulo, una fila de KPIs de
 * resumen y una cuadrícula de opciones (sub-módulos). Al tocar una opción navega a su pantalla.
 * Componente reutilizable para todos los módulos (Ventas, Compras, etc.) para dar una experiencia
 * consistente: primero el resumen del módulo, luego el cliente elige la acción.
 */
export function ModuleLanding({ title, subtitle, accentFrom, accentTo, stats = [], options }: ModuleLandingProps) {
  const navigate = useNavigate();

  return (
    <div className="mod">
      <button className="mod-back" onClick={() => navigate('/')}>
        <ArrowLeft size={16} /> Volver al inicio
      </button>

      <div className="mod-hero" style={{ background: `linear-gradient(135deg, ${accentFrom}, ${accentTo})` }}>
        <div className="mod-hero-glow" />
        <h1 className="mod-hero-title">{title}</h1>
        <p className="mod-hero-sub">{subtitle}</p>

        {stats.length > 0 && (
          <div className="mod-stats">
            {stats.map((s) => (
              <div key={s.label} className={`mod-stat mod-stat-${s.tone ?? 'accent'}`}>
                <span className="mod-stat-icon">{s.icon}</span>
                <div>
                  <div className="mod-stat-value">{s.value}</div>
                  <div className="mod-stat-label">{s.label}</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <motion.div className="mod-grid" variants={staggerContainer} initial="hidden" animate="visible">
        {options.map((o) => (
          <motion.button key={o.to} className="mod-option" variants={staggerItem}
            onClick={() => navigate(o.to)}
            whileHover={{ y: -4, scale: 1.02 }} whileTap={{ scale: 0.98 }}>
            <span className="mod-option-icon" style={{ background: `linear-gradient(135deg, ${o.from}, ${o.to2})` }}>
              {o.icon}
            </span>
            <div className="mod-option-text">
              <span className="mod-option-label">{o.label}</span>
              <span className="mod-option-sub">{o.sub}</span>
            </div>
          </motion.button>
        ))}
      </motion.div>
    </div>
  );
}
