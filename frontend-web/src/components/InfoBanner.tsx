import type { ReactNode } from 'react';
import './InfoBanner.css';

export type BannerTone = 'brand' | 'amber' | 'sky' | 'green' | 'violet' | 'neutral';

/**
 * Banner descriptivo premium reutilizable: icono con acento de color, título y una línea de
 * contexto que explica al usuario (cajero) qué hace la pantalla o el modo actual. Se usa en todo
 * el flujo para dar contexto consistente. Sigue las directrices de diseño (chips/acentos de color,
 * jerarquía, borde de acento).
 */
export function InfoBanner({
  icon, title, children, tone = 'brand',
}: {
  icon: ReactNode;
  title: string;
  children: ReactNode;
  tone?: BannerTone;
}) {
  return (
    <div className={`infobanner tone-${tone}`}>
      <span className="infobanner-icon">{icon}</span>
      <div className="infobanner-body">
        <strong className="infobanner-title">{title}</strong>
        <p className="infobanner-text">{children}</p>
      </div>
    </div>
  );
}
