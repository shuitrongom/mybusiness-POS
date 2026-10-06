/* =====================================================================
   BrandLogo — "logo mark" de marca para el catálogo de recargas/servicios.

   Cascada de fuentes (de mayor a menor prioridad):
     1) logoUrl  → logotipo OFICIAL autorizado (cuando lo tengas; columna logo_url).
     2) brandArt → distintivo SVG PROPIO por marca (no el logotipo registrado).
     3) monograma → tile con gradiente derivado del color oficial + iniciales.

   Así el catálogo se ve premium hoy (arte propio) y queda listo para mostrar
   los logotipos oficiales el día que se suban, sin tocar código.
   ===================================================================== */

import { useState } from 'react';
import { brandArtFor } from '@/components/brandArt';

interface BrandLogoProps {
  provider: string;
  color?: string | null;
  logoUrl?: string | null;
  size?: number;
}

function monogram(name: string): string {
  const clean = name.replace(/\(.*?\)/g, '').trim();
  const words = clean.split(/\s+/).filter(Boolean);
  if (words.length >= 2) return (words[0][0] + words[1][0]).toUpperCase();
  return clean.slice(0, 2).toUpperCase();
}

function shade(hex: string, percent: number): string {
  const h = hex.replace('#', '');
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h.slice(0, 6);
  const num = parseInt(full, 16);
  const r = Math.min(255, Math.max(0, ((num >> 16) & 0xff) + Math.round(255 * percent)));
  const g = Math.min(255, Math.max(0, ((num >> 8) & 0xff) + Math.round(255 * percent)));
  const b = Math.min(255, Math.max(0, (num & 0xff) + Math.round(255 * percent)));
  return `rgb(${r}, ${g}, ${b})`;
}

function isLight(hex: string): boolean {
  const h = hex.replace('#', '');
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h.slice(0, 6);
  const num = parseInt(full, 16);
  const r = (num >> 16) & 0xff;
  const g = (num >> 8) & 0xff;
  const b = num & 0xff;
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return lum > 0.72;
}

export function BrandLogo({ provider, color, logoUrl, size = 40 }: BrandLogoProps) {
  const [imgFailed, setImgFailed] = useState(false);
  const radius = Math.round(size * 0.28);

  // 1) Logotipo oficial (si existe y carga bien).
  if (logoUrl && !imgFailed) {
    return (
      <span className="brand-logo brand-logo-img" style={{ width: size, height: size, flex: `0 0 ${size}px`, borderRadius: radius }}>
        <img src={logoUrl} alt={provider} width={size} height={size} loading="lazy"
          onError={() => setImgFailed(true)} style={{ borderRadius: radius }} />
      </span>
    );
  }

  // 2) Arte SVG propio de la marca.
  const art = brandArtFor(provider);
  if (art) {
    return (
      <span className="brand-logo brand-logo-art" style={{ width: size, height: size, flex: `0 0 ${size}px`, borderRadius: radius }}>
        {art}
      </span>
    );
  }

  // 3) Monograma sobre gradiente derivado del color oficial.
  const base = color && /^#?[0-9a-fA-F]{3,8}$/.test(color) ? (color.startsWith('#') ? color : `#${color}`) : '#64748b';
  const light = shade(base, 0.14);
  const dark = shade(base, -0.16);
  const fg = isLight(base) ? '#0f172a' : '#ffffff';
  const fontSize = Math.round(size * 0.36);

  return (
    <span
      className="brand-logo brand-logo-mono-tile"
      style={{
        width: size, height: size, flex: `0 0 ${size}px`, borderRadius: radius,
        background: `linear-gradient(135deg, ${light} 0%, ${base} 45%, ${dark} 100%)`,
        color: fg, fontSize,
      }}
      aria-hidden="true"
    >
      <span className="brand-logo-mono">{monogram(provider)}</span>
      <span className="brand-logo-gloss" style={{ borderRadius: radius }} />
    </span>
  );
}
