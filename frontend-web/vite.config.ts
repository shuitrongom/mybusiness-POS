import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';

// Configuración de Vite: React + PWA (instalable y con soporte offline) + alias @.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
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
        // Cachea la app para que funcione offline.
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // En desarrollo, redirige las llamadas /api al backend local.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
  },
});
