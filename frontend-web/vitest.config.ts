import { defineConfig } from 'vitest/config';

/**
 * Configuración de Vitest aislada del vite.config.ts.
 *
 * Se mantiene separada a propósito: incluir los plugins de la app (React, PWA) en el runner de
 * pruebas provoca conflictos de inicialización del worker en Vitest 5 (error "reading 'config'").
 * Aquí solo declaramos lo que las pruebas necesitan: el alias '@' y el entorno jsdom. Los tests
 * importan sus utilidades explícitamente desde 'vitest' (sin globals), lo que es más type-safe.
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': `${import.meta.dirname}/src`,
    },
  },
  test: {
    environment: 'jsdom',
  },
});
