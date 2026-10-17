import { useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Search, ChevronDown, Check, Package } from 'lucide-react';
import './ProductPicker.css';

export interface PickerProduct {
  id: number;
  name: string;
  unit?: string | null;
  cost?: number | null;
}

/**
 * Selector PREMIUM de artículos por SELECCIÓN (no exige teclear): al abrir muestra la lista
 * completa de productos; un campo de filtro opcional la acota en vivo. Pensado para Entradas y
 * Salidas de inventario, donde el usuario prefiere elegir de una lista a escribir a ciegas.
 *
 * - Click en el botón: abre/cierra el panel con TODA la lista.
 * - Filtro: acota por nombre o id, sin ser obligatorio.
 * - Teclado: ↑/↓ navega, Enter selecciona, Esc cierra.
 * - Marca los artículos ya agregados (selectedIds) con una palomita, y evita volver a agregarlos.
 */
export function ProductPicker({
  products, onPick, selectedIds, placeholder = 'Seleccionar artículo…',
}: {
  products: PickerProduct[];
  onPick: (p: PickerProduct) => void;
  selectedIds?: Set<number>;
  placeholder?: string;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return products;
    return products.filter((p) => p.name.toLowerCase().includes(q) || String(p.id) === q);
  }, [products, query]);

  // Cierra al hacer click fuera.
  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  // Al abrir, enfoca el filtro y resetea el resaltado.
  useEffect(() => {
    if (open) { setActive(0); setTimeout(() => inputRef.current?.focus(), 10); }
    else { setQuery(''); }
  }, [open]);

  const choose = (p: PickerProduct) => {
    onPick(p);
    setQuery('');
    setActive(0);
    // Se cierra al elegir para no tapar la tabla de renglones. Para agregar otro, se reabre.
    setOpen(false);
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, filtered.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
    else if (e.key === 'Enter') { e.preventDefault(); const p = filtered[active]; if (p) choose(p); }
    else if (e.key === 'Escape') { e.preventDefault(); setOpen(false); }
  };

  return (
    <div className="ppick" ref={rootRef}>
      <button type="button" className={`ppick-trigger ${open ? 'is-open' : ''}`} onClick={() => setOpen((o) => !o)}>
        <Package size={16} />
        <span className="ppick-trigger-label">{placeholder}</span>
        <ChevronDown size={16} className="ppick-chevron" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div className="ppick-panel"
            initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.14 }}>
            <div className="ppick-filter">
              <Search size={15} />
              <input
                ref={inputRef}
                value={query}
                onChange={(e) => { setQuery(e.target.value); setActive(0); }}
                onKeyDown={onKeyDown}
                placeholder="Filtrar por nombre… (opcional)"
              />
              <span className="ppick-count">{filtered.length}</span>
            </div>
            <div className="ppick-list" role="listbox">
              {filtered.map((p, i) => {
                const already = selectedIds?.has(p.id);
                return (
                  <button
                    key={p.id}
                    type="button"
                    role="option"
                    aria-selected={i === active}
                    className={`ppick-item ${i === active ? 'is-active' : ''} ${already ? 'is-added' : ''}`}
                    onMouseEnter={() => setActive(i)}
                    onClick={() => choose(p)}
                  >
                    <span className="ppick-item-name">{p.name}</span>
                    <span className="ppick-item-meta">{p.unit || 'pz'}</span>
                    {already && <Check size={14} className="ppick-item-check" />}
                  </button>
                );
              })}
              {filtered.length === 0 && (
                <div className="ppick-empty">Sin artículos que coincidan.</div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
