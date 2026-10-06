import { useQuery } from '@tanstack/react-query';
import {
  Boxes, Package, Ruler, FileSpreadsheet, ArrowDownToLine, ArrowUpFromLine,
  ClipboardList, Barcode, PackageSearch, ArrowLeftRight, Gauge, Tags, Zap,
  Layers, DollarSign, TriangleAlert, Smartphone, History,
} from 'lucide-react';
import { api } from '@/lib/api';
import { ModuleLanding, type ModuleOption, type ModuleStat } from '@/components/ModuleLanding';

interface Summary { skus: number; units: number; value: number; alerts: number; }

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

/**
 * Página del módulo INVENTARIO: resumen (SKUs, unidades, valor, alertas) y todas las opciones de
 * la suite. El cliente entra desde el Inicio y elige la acción.
 */
export function InventoryModulePage() {
  const summary = useQuery({
    queryKey: ['inventory', 'summary', 'all'],
    queryFn: async () => (await api.get<Summary>('/inventory/summary')).data,
  });
  const s = summary.data;

  const stats: ModuleStat[] = [
    { label: 'Productos (SKUs)', value: int(s?.skus ?? 0), icon: <Layers size={20} /> },
    { label: 'Unidades', value: int(s?.units ?? 0), icon: <Boxes size={20} /> },
    { label: 'Valor del inventario', value: money(s?.value ?? 0), icon: <DollarSign size={20} /> },
    { label: 'Alertas de stock', value: int(s?.alerts ?? 0), icon: <TriangleAlert size={20} />, tone: 'warning' },
  ];

  const options: ModuleOption[] = [
    { to: '/inventory', label: 'Catálogo y existencias', sub: 'Existencias, alertas y operaciones', icon: <Boxes size={26} />, from: '#0891b2', to2: '#06b6d4' },
    { to: '/products', label: 'Catálogo de artículos', sub: 'Editor completo de productos', icon: <Package size={26} />, from: '#2563eb', to2: '#3b82f6' },
    { to: '/inventory-quick', label: 'Alta rápida de artículos', sub: 'Crea productos al vuelo', icon: <Zap size={26} />, from: '#16a34a', to2: '#22c55e' },
    { to: '/inventory-variants', label: 'Tallas y colores', sub: 'Genera variantes por modelo', icon: <Ruler size={26} />, from: '#7c3aed', to2: '#8b5cf6' },
    { to: '/products?import=1', label: 'Importar desde Excel', sub: 'Alta de artículos en lote', icon: <FileSpreadsheet size={26} />, from: '#0d9488', to2: '#14b8a6' },
    { to: '/inventory-entries', label: 'Entradas al inventario', sub: 'Documentos de entrada', icon: <ArrowDownToLine size={26} />, from: '#ea580c', to2: '#f97316' },
    { to: '/inventory-exits', label: 'Salidas y traspasos', sub: 'Salidas y movimientos entre sucursales', icon: <ArrowUpFromLine size={26} />, from: '#e11d48', to2: '#f43f5e' },
    { to: '/inventory-physical', label: 'Inventario físico', sub: 'Conteo, marbetes y diferencias', icon: <ClipboardList size={26} />, from: '#4f46e5', to2: '#6366f1' },
    { to: '/inventory-serials', label: 'Números de serie', sub: 'Series por producto', icon: <Barcode size={26} />, from: '#0369a1', to2: '#0ea5e9' },
    { to: '/inventory-lots', label: 'Números de lote', sub: 'Lotes y caducidades', icon: <PackageSearch size={26} />, from: '#9333ea', to2: '#a855f7' },
    { to: '/inventory-transfers', label: 'Traspasos entre sucursales', sub: 'Mueve mercancía entre almacenes', icon: <ArrowLeftRight size={26} />, from: '#0891b2', to2: '#22d3ee' },
    { to: '/inventory-quality', label: 'Calidad de inventario', sub: 'Clasificación ABC de tu stock', icon: <Gauge size={26} />, from: '#c026d3', to2: '#d946ef' },
    { to: '/inventory-labels', label: 'Etiquetas de código de barras', sub: 'Genera e imprime etiquetas', icon: <Tags size={26} />, from: '#475569', to2: '#64748b' },
    { to: '/inventory-stockapp', label: 'Inventario físico StockApp', sub: 'Importa conteos y entradas por Excel o QR', icon: <Smartphone size={26} />, from: '#334155', to2: '#475569' },
    { to: '/inventory-kardex', label: 'Kardex de movimientos', sub: 'Historial por producto', icon: <History size={26} />, from: '#0f766e', to2: '#14b8a6' },
  ];

  return (
    <ModuleLanding
      title="Inventario"
      subtitle="Existencias, entradas y salidas, tallas y colores, series, lotes, conteo físico, calidad y etiquetas"
      accentFrom="#155e75"
      accentTo="#06b6d4"
      stats={stats}
      options={options}
    />
  );
}
