import { useQuery } from '@tanstack/react-query';
import {
  FileText, ShoppingCart, Undo2, Truck, Upload, HandCoins,
  CalendarClock, FileCode, DollarSign, Package,
} from 'lucide-react';
import { api } from '@/lib/api';
import { ModuleLanding, type ModuleOption, type ModuleStat } from '@/components/ModuleLanding';

interface PayablesSummary { totalPending: number; openCount: number; overdueCount: number; }

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

/**
 * Página del módulo COMPRAS: resumen (por pagar, cuentas abiertas/vencidas) y las opciones de la
 * suite. El cliente entra desde el Inicio y elige la acción.
 */
export function PurchasingModulePage() {
  const payables = useQuery({
    queryKey: ['payables-summary'],
    queryFn: async () => (await api.get<PayablesSummary>('/purchasing/payables/summary')).data,
  });
  const suppliers = useQuery({
    queryKey: ['suppliers-count'],
    queryFn: async () => (await api.get<unknown[]>('/purchasing/suppliers')).data,
  });

  const stats: ModuleStat[] = [
    { label: 'Por pagar', value: money(payables.data?.totalPending ?? 0), icon: <DollarSign size={20} /> },
    { label: 'Cuentas abiertas', value: int(payables.data?.openCount ?? 0), icon: <HandCoins size={20} /> },
    { label: 'Vencidas', value: int(payables.data?.overdueCount ?? 0), icon: <CalendarClock size={20} />, tone: 'danger' },
    { label: 'Proveedores', value: int(suppliers.data?.length ?? 0), icon: <Truck size={20} /> },
  ];

  const options: ModuleOption[] = [
    { to: '/purchase-orders', label: 'Órdenes de compra', sub: 'Solicita mercancía a proveedores', icon: <FileText size={26} />, from: '#ea580c', to2: '#f97316' },
    { to: '/purchases', label: 'Compras', sub: 'Recibe mercancía con impuestos', icon: <ShoppingCart size={26} />, from: '#16a34a', to2: '#22c55e' },
    { to: '/purchase-returns', label: 'Devolución de compras', sub: 'Devuelve mercancía al proveedor', icon: <Undo2 size={26} />, from: '#e11d48', to2: '#f43f5e' },
    { to: '/suppliers', label: 'Proveedores', sub: 'Catálogo y crédito', icon: <Truck size={26} />, from: '#7c3aed', to2: '#8b5cf6' },
    { to: '/suppliers?import=1', label: 'Importar proveedores', sub: 'Alta en lote desde Excel', icon: <Upload size={26} />, from: '#0d9488', to2: '#14b8a6' },
    { to: '/payables', label: 'Cuentas por pagar', sub: 'Saldos y abonos a proveedores', icon: <HandCoins size={26} />, from: '#0891b2', to2: '#06b6d4' },
    { to: '/supplier-visits', label: 'Visita de proveedores', sub: 'Rol y agenda de visitas', icon: <CalendarClock size={26} />, from: '#4f46e5', to2: '#6366f1' },
    { to: '/purchase-xml', label: 'Compras XML', sub: 'Importa CFDI del proveedor', icon: <FileCode size={26} />, from: '#0369a1', to2: '#0ea5e9' },
    { to: '/inventory', label: 'Inventario', sub: 'Existencias que alimentan las compras', icon: <Package size={26} />, from: '#475569', to2: '#64748b' },
  ];

  return (
    <ModuleLanding
      title="Compras"
      subtitle="Órdenes, recepción con impuestos, devoluciones, proveedores, cuentas por pagar y CFDI"
      accentFrom="#c2410c"
      accentTo="#f97316"
      stats={stats}
      options={options}
    />
  );
}
