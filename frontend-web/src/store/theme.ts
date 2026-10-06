import { create } from 'zustand';

/**
 * Gestión del tema visual (claro / oscuro) de la aplicación.
 *
 * - Persiste la elección del usuario en localStorage.
 * - Si el usuario no ha elegido, respeta la preferencia del sistema operativo.
 * - Aplica el atributo data-theme en <html>, que activa los tokens del tema oscuro en theme.css.
 */
export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'mbs-theme';

/** Determina el tema inicial: preferencia guardada o, en su defecto, la del sistema. */
function resolveInitialTheme(): Theme {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  return prefersDark ? 'dark' : 'light';
}

/** Aplica el tema al documento (atributo en <html>) y lo persiste. */
function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem(STORAGE_KEY, theme);
}

interface ThemeState {
  theme: Theme;
  toggle: () => void;
  setTheme: (theme: Theme) => void;
}

export const useTheme = create<ThemeState>((set, get) => ({
  theme: resolveInitialTheme(),
  toggle: () => {
    const next: Theme = get().theme === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    set({ theme: next });
  },
  setTheme: (theme: Theme) => {
    applyTheme(theme);
    set({ theme });
  },
}));

/**
 * Inicializa el tema en el arranque de la app (antes de renderizar), para evitar el parpadeo
 * de tema incorrecto ("flash of wrong theme"). Se llama una sola vez desde main.tsx.
 */
export function initTheme(): void {
  applyTheme(resolveInitialTheme());
}
