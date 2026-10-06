import { create } from 'zustand';

/**
 * Store de notificaciones tipo "toast". Permite mostrar mensajes de éxito, error o info desde
 * cualquier parte de la app (por ejemplo, tras crear un negocio o guardar un plan). Los toasts se
 * autodescartan tras unos segundos y se animan con Motion en el componente ToastHost.
 */
export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  title: string;
  message?: string;
}

interface ToastState {
  toasts: Toast[];
  push: (toast: Omit<Toast, 'id'>) => void;
  dismiss: (id: number) => void;
}

let nextId = 1;

export const useToast = create<ToastState>((set) => ({
  toasts: [],
  push: (toast) => {
    const id = nextId++;
    set((s) => ({ toasts: [...s.toasts, { ...toast, id }] }));
    // Autodescartar tras 4.5s (los errores duran un poco más para poder leerlos).
    const ttl = toast.kind === 'error' ? 6000 : 4500;
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
    }, ttl);
  },
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

/** Atajos para no repetir el objeto en cada llamada. */
export const toast = {
  success: (title: string, message?: string) => useToast.getState().push({ kind: 'success', title, message }),
  error: (title: string, message?: string) => useToast.getState().push({ kind: 'error', title, message }),
  info: (title: string, message?: string) => useToast.getState().push({ kind: 'info', title, message }),
};
