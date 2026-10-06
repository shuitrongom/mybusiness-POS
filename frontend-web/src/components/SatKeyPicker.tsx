import { useEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { api } from '@/lib/api';

interface SatKey { clave: string; descripcion?: string; nombre?: string; }

interface SatKeyPickerProps {
  kind: 'prod-serv' | 'unit';
  value: string;
  onSelect: (clave: string, label: string) => void;
  placeholder?: string;
}

/**
 * Buscador de claves del SAT (c_ClaveProdServ o c_ClaveUnidad) con autocompletar.
 * Consulta /invoicing/sat/{kind} conforme se teclea y permite elegir una clave del catálogo.
 */
export function SatKeyPicker({ kind, value, onSelect, placeholder }: SatKeyPickerProps) {
  const [query, setQuery] = useState(value);
  const [results, setResults] = useState<SatKey[]>([]);
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => { setQuery(value); }, [value]);

  useEffect(() => {
    if (!open) return;
    const t = setTimeout(() => {
      api.get<SatKey[]>(`/invoicing/sat/${kind}`, { params: { q: query } })
        .then((r) => setResults(r.data)).catch(() => setResults([]));
    }, 220);
    return () => clearTimeout(t);
  }, [query, open, kind]);

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  return (
    <div className="sat-picker" ref={boxRef}>
      <div className="cust-search">
        <Search size={16} />
        <input
          value={query}
          onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)}
          placeholder={placeholder ?? 'Buscar clave SAT…'}
        />
      </div>
      {open && results.length > 0 && (
        <div className="sat-picker-results">
          {results.map((r) => {
            const label = r.descripcion ?? r.nombre ?? '';
            return (
              <div key={r.clave} className="sat-picker-item"
                onClick={() => { onSelect(r.clave, label); setQuery(r.clave); setOpen(false); }}>
                <span className="clave">{r.clave}</span>
                <span className="desc">{label}</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
