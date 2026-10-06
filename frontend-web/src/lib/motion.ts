import type { Transition, Variants } from 'motion/react';

/**
 * Presets de animación reutilizables (Motion / ex Framer Motion) para toda la app.
 *
 * Centralizar las variantes garantiza consistencia visual y facilita respetar la preferencia de
 * "reducir movimiento" del sistema operativo. Motion ya honra `prefers-reduced-motion` cuando se
 * usa el hook useReducedMotion; aquí definimos curvas y tiempos suaves y "premium" (spring).
 */

/** Transición spring suave para entradas y cambios de layout. */
export const springSoft: Transition = {
  type: 'spring',
  stiffness: 260,
  damping: 26,
  mass: 0.9,
};

/** Transición rápida para micro-interacciones (hover, tap). */
export const quick: Transition = { duration: 0.18, ease: 'easeOut' };

/** Aparición desde abajo con desvanecido (tarjetas, secciones). */
export const fadeInUp: Variants = {
  hidden: { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: springSoft },
  exit: { opacity: 0, y: 8, transition: quick },
};

/** Aparición con leve escala (modales, elementos destacados). */
export const popIn: Variants = {
  hidden: { opacity: 0, scale: 0.96, y: 8 },
  visible: { opacity: 1, scale: 1, y: 0, transition: springSoft },
  exit: { opacity: 0, scale: 0.97, y: 6, transition: quick },
};

/** Contenedor que revela a sus hijos de forma escalonada (listas, rejillas). */
export const staggerContainer: Variants = {
  hidden: {},
  visible: {
    transition: { staggerChildren: 0.05, delayChildren: 0.04 },
  },
};

/** Hijo de un contenedor con stagger (filas de tabla, tarjetas de lista). */
export const staggerItem: Variants = {
  hidden: { opacity: 0, y: 12 },
  visible: { opacity: 1, y: 0, transition: springSoft },
};

/** Props de interacción para botones premium (elevación al hover, hundido al pulsar). */
export const pressable = {
  whileHover: { y: -1, scale: 1.015 },
  whileTap: { scale: 0.97 },
  transition: quick,
};
