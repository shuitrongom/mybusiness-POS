import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// Configuración de Vite: React + PWA (instalable y con soporte offline) + alias @.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // En desarrollo NO registramos el Service Worker: evita que un bundle cacheado
      // oculte los cambios recién compilados (causa típica de "no veo mis cambios").
      // La PWA se activa solo en el build de producción.
      devOptions: {
        enabled: false,
      },
      manifest: {
        name: 'MyBusiness Silva',
        short_name: 'MB Silva',
        description: 'Punto de venta en la nube',
        theme_color: '#1a2b5c',
        background_color: '#0f172a',
        display: 'standalone',
        icons: [],
      },
      workbox: {
        // Cachea la app para que funcione offline (solo en producción).
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        // Toma control inmediato y limpia cachés viejas al actualizar.
        clientsClaim: true,
        skipWaiting: true,
      },
    }),
  ],
  resolve: {
    alias: {
      // import.meta.dirname (estándar ESM, Node 20.11+) en lugar de __dirname,
      // compatible con el cargador de config nativo de Vite 8.
      '@': `${import.meta.dirname}/src`,
    },
  },
  server: {
    // Puerto 4300 para no chocar con otros proyectos (que usan 4200/8080/8081).
    port: 4300,
    proxy: {
      // En desarrollo, redirige las llamadas /api al backend local (puerto 8082).
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
});
