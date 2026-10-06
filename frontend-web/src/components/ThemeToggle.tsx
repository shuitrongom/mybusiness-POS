import { Moon, Sun } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import { useTheme } from '@/store/theme';
import './theme-toggle.css';

/**
 * Interruptor premium de tema claro/oscuro. Muestra sol o luna según el tema activo, con una
 * transición animada al alternar. Accesible por teclado y con etiqueta descriptiva.
 */
export function ThemeToggle() {
  const theme = useTheme((s) => s.theme);
  const toggle = useTheme((s) => s.toggle);
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={toggle}
      aria-label={isDark ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'}
      aria-pressed={isDark}
      title={isDark ? 'Tema claro' : 'Tema oscuro'}
    >
      <AnimatePresence mode="wait" initial={false}>
        <motion.span
          key={theme}
          className="theme-toggle-icon"
          initial={{ y: -12, opacity: 0, rotate: -90 }}
          animate={{ y: 0, opacity: 1, rotate: 0 }}
          exit={{ y: 12, opacity: 0, rotate: 90 }}
          transition={{ duration: 0.22, ease: 'easeOut' }}
        >
          {isDark ? <Moon size={18} strokeWidth={1.9} /> : <Sun size={18} strokeWidth={1.9} />}
        </motion.span>
      </AnimatePresence>
    </button>
  );
}
