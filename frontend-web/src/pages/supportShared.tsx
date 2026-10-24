import type { ReactNode } from 'react';
import { Bug, Lightbulb, Monitor, Smartphone, HelpCircle } from 'lucide-react';

/**
 * Tipos y helpers compartidos del buzón de soporte (panel del dueño y bandeja global del
 * super-admin). Centraliza el contrato REST de FEAT-002, los metadatos de chips de color por
 * tipo/estado, la máquina de transiciones visibles y el formato de fecha/dispositivo. Un solo lugar
 * para la jerarquía visual, coherente con las directrices de diseño.
 */

export type TicketType = 'ERROR' | 'MEJORA';
export type TicketStatus = 'NUEVO' | 'EN_REVISION' | 'RESUELTO' | 'CERRADO' | 'DESCARTADO';

/** Forma de lista (GET /tickets y /admin/support-tickets). */
export interface TicketListItem {
  publicId: string;
  businessId: number | null;
  type: TicketType;
  title: string;
  status: TicketStatus;
  reporterEmail: string | null;
  reporterRole: string;
  device: string;
  imageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface TicketImageView {
  url: string;
  publicId: string;
  width: number | null;
  height: number | null;
  bytes: number | null;
}

/** Forma de detalle (GET /tickets/{publicId}). */
export interface TicketView {
  publicId: string;
  businessId: number | null;
  tenantSchema: string;
  reporterUserId: string;
  reporterEmail: string | null;
  reporterRole: string;
  type: TicketType;
  title: string;
  description: string;
  screenPath: string | null;
  appVersion: string | null;
  userAgent: string | null;
  device: string;
  status: TicketStatus;
  resolutionNote: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
  images: TicketImageView[];
}

/** Chips de tipo: ERROR ámbar/rojo, MEJORA azul/violeta (jerarquía por color del steering). */
export const TYPE_META: Record<TicketType, { label: string; cls: string; icon: ReactNode }> = {
  ERROR: { label: 'Error', cls: 'is-error', icon: <Bug size={13} /> },
  MEJORA: { label: 'Mejora', cls: 'is-mejora', icon: <Lightbulb size={13} /> },
};

/** Chips de estado: NUEVO brand, EN_REVISION ámbar, RESUELTO verde, CERRADO/DESCARTADO gris. */
export const STATUS_META: Record<TicketStatus, { label: string; cls: string }> = {
  NUEVO: { label: 'Nuevo', cls: 'is-new' },
  EN_REVISION: { label: 'En revisión', cls: 'is-review' },
  RESUELTO: { label: 'Resuelto', cls: 'is-done' },
  CERRADO: { label: 'Cerrado', cls: 'is-closed' },
  DESCARTADO: { label: 'Descartado', cls: 'is-discarded' },
};

/** Opciones del filtro de estado (incluye "Todos"). */
export const STATUS_FILTERS: { value: string; label: string }[] = [
  { value: '', label: 'Todos los estados' },
  { value: 'NUEVO', label: 'Nuevo' },
  { value: 'EN_REVISION', label: 'En revisión' },
  { value: 'RESUELTO', label: 'Resuelto' },
  { value: 'CERRADO', label: 'Cerrado' },
  { value: 'DESCARTADO', label: 'Descartado' },
];

/** Transiciones válidas desde cada estado (espejo de TicketStatus.canTransitionTo del backend). */
export const OWNER_TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  NUEVO: ['EN_REVISION', 'DESCARTADO'],
  EN_REVISION: ['RESUELTO', 'DESCARTADO'],
  RESUELTO: ['CERRADO', 'EN_REVISION'],
  CERRADO: [],
  DESCARTADO: [],
};

/** Icono por dispositivo clasificado por el backend (mobile|desktop|unknown). */
export function deviceIcon(device: string): ReactNode {
  if (device === 'mobile') return <Smartphone size={13} />;
  if (device === 'desktop') return <Monitor size={13} />;
  return <HelpCircle size={13} />;
}

/** Fecha/hora apiladas (es-MX), con una versión completa para el detalle. */
export function fmtDateTime(iso: string | null): { date: string; time: string; full: string } {
  if (!iso) return { date: '—', time: '', full: '—' };
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return { date: '—', time: '', full: '—' };
  return {
    date: d.toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' }),
    time: d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' }),
    full: d.toLocaleString('es-MX', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }),
  };
}
