import { useQuery } from '@tanstack/react-query';
import { PackagePlus, Boxes, Trash2, DollarSign } from 'lucide-react';
import { api } from '@/lib/api';
import { ModuleLanding, type ModuleOption, type ModuleStat } from '@/components/ModuleLanding';

/**
 * Un renglón de producción (una fila POR renglón) tal como lo devuelve GET /bakery/productions.
 * Solo tipamos lo que esta pantalla consume para los KPIs del día.
 */
interface ProductionRow {
  quantity: number | null;
  lineTotal: number | null;
}

/** Un registro de merma del día tal como lo devuelve GET /bakery/waste. */
interface WasteRow {
  quantity: number | null;
  difference: number | null;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

/** Fecha de hoy en formato ISO yyyy-MM-dd (día natural local). */
function todayIso(): string {
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Página del módulo PANADERÍA: resumen del día (producido, valor producido, mermas y su importe)
 * y las opciones de la suite (captura de producción, existencias y merma). El cliente entra desde
 * el Inicio y elige la acción.
 */
export function BakeryModulePage() {
  const today = todayIso();

  const productions = useQuery({
    queryKey: ['bakery', 'productions', 'today', today],
    queryFn: async () =>
      (await api.get<ProductionRow[]>('/bakery/productions', { params: { from: today, to: today, limit: 500 } })).data,
  });
  const waste = useQuery({
    queryKey: ['bakery', 'waste', 'today', today],
    queryFn: async () =>
      (await api.get<WasteRow[]>('/bakery/waste', { params: { from: today, to: today } })).data,
  });

  const prodRows = productions.data ?? [];
  const wasteRows = waste.data ?? [];

  const producedUnits = prodRows.reduce((s, r) => s + (r.quantity ?? 0), 0);
  const producedValue = prodRows.reduce((s, r) => s + (r.lineTotal ?? 0), 0);
  const wasteUnits = wasteRows.reduce((s, r) => s + (r.quantity ?? 0), 0);
  // difference = (entry_price - waste_price) por unidad; el importe de merma es la pérdida total.
  const wasteValue = wasteRows.reduce((s, r) => s + (r.difference ?? 0) * (r.quantity ?? 0), 0);

  const stats: ModuleStat[] = [
    { label: 'Producido hoy', value: int(producedUnits), icon: <PackagePlus size={20} /> },
    { label: 'Valor producido', value: money(producedValue), icon: <DollarSign size={20} /> },
    { label: 'Piezas en merma', value: int(wasteUnits), icon: <Trash2 size={20} />, tone: 'warning' },
    { label: 'Pérdida por merma', value: money(wasteValue), icon: <DollarSign size={20} />, tone: 'danger' },
  ];

  const options: ModuleOption[] = [
    { to: '/panaderia-entradas', label: 'Captura de producción', sub: 'Registra el pan producido del día', icon: <PackagePlus size={26} />, from: '#b45309', to2: '#d97706' },
    { to: '/panaderia-existencias', label: 'Existencias por día', sub: 'Producido, vendido y restante por producto', icon: <Boxes size={26} />, from: '#0891b2', to2: '#06b6d4' },
    { to: '/panaderia-merma', label: 'Merma vendida', sub: 'Historial de pan vendido como merma', icon: <Trash2 size={26} />, from: '#e11d48', to2: '#f43f5e' },
  ];

  return (
    <ModuleLanding
      title="Panadería"
      subtitle="Producción de pan por día, control de no-sobreventa y merma vendible con trazabilidad"
      accentFrom="#92400e"
      accentTo="#d97706"
      stats={stats}
      options={options}
    />
  );
}
