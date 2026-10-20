import './scope-selector.css';

/** Alcance de reporte respecto al MODO PRUEBA. */
export type ReportScope = 'real' | 'test' | 'both';

const OPTIONS: { value: ReportScope; label: string; tone: string }[] = [
  { value: 'real', label: 'Histórico real', tone: 'green' },
  { value: 'test', label: 'Prueba', tone: 'amber' },
  { value: 'both', label: 'Ambos', tone: 'neutral' },
];

/**
 * Selector INFORMATIVO de 3 vistas (Histórico real / Prueba / Ambos) para separar
 * las ventas/cortes de práctica del histórico real en dashboards e historiales.
 * No borra nada: solo cambia el filtro. Chips premium según las directrices de
 * diseño (acento brand en el activo, tonos coherentes verde/ámbar/neutral).
 */
export function ScopeSelector({
  value, onChange,
}: {
  value: ReportScope;
  onChange: (v: ReportScope) => void;
}) {
  return (
    <div className="scope-selector" role="tablist" aria-label="Filtro de histórico (modo prueba)">
      {OPTIONS.map((o) => (
        <button
          key={o.value}
          type="button"
          role="tab"
          aria-selected={value === o.value}
          className={`scope-chip tone-${o.tone} ${value === o.value ? 'is-active' : ''}`}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
