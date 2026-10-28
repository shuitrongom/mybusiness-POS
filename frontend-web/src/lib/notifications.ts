import { createElement, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, LifeBuoy } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';

/**
 * Centro de notificaciones (Pieza 2, §6/§8). Tipos del contrato REST, mapa de metadatos de render
 * por tipo (icono + tono + ruta de deep-link según refType) y hooks de react-query.
 *
 * Nota de implementación: los iconos se construyen con `createElement` en lugar de JSX literal
 * para que este archivo conserve la extensión `.ts` (TypeScript no admite JSX en `.ts`). El
 * `ReactNode` resultante es idéntico al de la sintaxis JSX; no cambia nada visible.
 */

export interface NotificationView {
  publicId: string;
  type: string;
  title: string;
  body: string | null;
  refType: string | null;
  refId: string | null;
  read: boolean;
  createdAt: string;
}

export interface InboxResponse {
  items: NotificationView[];
  unreadCount: number;
}

/** Metadatos de render por tipo: icono, tono, ruta de navegación por refType. */
export const NOTIF_META: Record<string, {
  icon: ReactNode; tone: string; navRoute?: (refId: string, isSuperAdmin: boolean) => string
}> = {
  SUPPORT_REPLY: {
    icon: createElement(LifeBuoy, { size: 16 }),
    tone: 'brand',
    navRoute: (refId) => `/support?ticket=${refId}`,
  },
  SUPPORT_REPLY_FROM_REPORTER: {
    icon: createElement(LifeBuoy, { size: 16 }),
    tone: 'violet',
    navRoute: (refId) => `/admin-support?ticket=${refId}`,
  },
  SUPPORT_NEW_TICKET: {
    icon: createElement(LifeBuoy, { size: 16 }),
    tone: 'amber',
    navRoute: (refId) => `/admin-support?ticket=${refId}`,
  },
};

/** Fallback para tipos desconocidos (extensibilidad, §8). */
export const DEFAULT_NOTIF_META = { icon: createElement(Bell, { size: 16 }), tone: 'neutral' };

// ---- Hooks de react-query ----

/** Poll de no-leídas cada 45 s. Pausa en pestaña en segundo plano (D11/M-R3-2). */
export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: async () =>
      (await api.get<{ unreadCount: number }>('/notifications/unread-count')).data.unreadCount,
    refetchInterval: () => (document.hidden ? false : 45_000),
    refetchIntervalInBackground: false,
    staleTime: 30_000,
  });
}

/** Bandeja completa (items + unreadCount) — se llama al abrir el panel. */
export function useInbox() {
  return useQuery({
    queryKey: ['notifications', 'inbox'],
    queryFn: async () => (await api.get<InboxResponse>('/notifications')).data,
  });
}

/** Marcar una leída — mutación optimista con rollback en error (M4). */
export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (publicId: string) => {
      await api.post(`/notifications/${publicId}/read`);
      return publicId;
    },
    onMutate: async (publicId) => {
      // Optimista: marcar en cache
      await qc.cancelQueries({ queryKey: ['notifications'] });
      const prev = qc.getQueryData<InboxResponse>(['notifications', 'inbox']);
      if (prev) {
        qc.setQueryData<InboxResponse>(['notifications', 'inbox'], {
          items: prev.items.map((n) => n.publicId === publicId ? { ...n, read: true } : n),
          unreadCount: Math.max(0, prev.unreadCount - (prev.items.find((n) => n.publicId === publicId && !n.read) ? 1 : 0)),
        });
      }
      return { prev };
    },
    onError: (_err, _id, ctx) => {
      if (ctx?.prev) qc.setQueryData(['notifications', 'inbox'], ctx.prev);
      toast.error('Error', 'No se pudo marcar la notificación como leída.');
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });
}

/** Marcar todas leídas. */
export function useMarkAllRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => (await api.post<{ updated: number }>('/notifications/read-all')).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
    onError: () => toast.error('Error', 'No se pudo marcar todas como leídas.'),
  });
}
