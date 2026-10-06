import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';
import { ToastHost } from './components/ToastHost';
import { initTheme } from './store/theme';
import './styles/theme.css';

// Aplica el tema (claro/oscuro) antes de renderizar para evitar parpadeo de tema incorrecto.
initTheme();

// Elimina de raíz cualquier Service Worker y caché previos: evita que un bundle viejo (mezcla de
// JS/CSS de builds distintos) siga sirviéndose y oculte cambios recientes (ej. iconos que no
// aparecen). La PWA se desactivó en desarrollo; aquí limpiamos lo ya registrado.
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.getRegistrations().then((regs) => regs.forEach((r) => r.unregister()));
  if (typeof caches !== 'undefined') {
    caches.keys().then((keys) => keys.forEach((k) => caches.delete(k)));
  }
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
        <ToastHost />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
