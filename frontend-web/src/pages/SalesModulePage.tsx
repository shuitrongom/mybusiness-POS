import { useQuery } from '@tanstack/react-query';
import { useSession } from '@/store/session';
import { decodeToken, canUseModule } from '@/lib/jwt';
import {
  ShoppingCart, Undo2, Tag, Wallet, HandCoins, Receipt, DollarSign, TrendingUp,
} from 'lucide-react';
import { api } from '@/lib/api';
import { ModuleLanding, type ModuleOption, type ModuleStat } from '@/components/ModuleLanding';

interface DashboardSummary { salesCount: number; salesTotal: number; itemsSold: number; }
interface Summary { totalPending: number; openCount: number; overdueCount: number; }
/** Resumen de turno del cajero (subset de /shifts/my-summary) para los KPIs de su landing. */
interface ShiftSummary { totalSales: number; salesCount: number; averageTicket: number; }

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

/**
 * Página del módulo VENTAS: muestra un resumen (ventas de hoy, ticket promedio, cartera por
 * cobrar) y las opciones del módulo. El cliente entra aquí desde el Inicio y elige la acción.
 */
export function SalesModulePage() {
  const claims = decodeToken(useSession((s) => s.token));
  const canSeeBi = canUseModule(claims, 'bi');
  const canSeeCustomers = canUseModule(claims, 'customers');

  // DUEÑO/ADMIN: números GLOBALES del negocio (módulo bi). CAJERO: los MISMOS KPIs pero desde su
  // propio turno (/shifts/my-summary, que su módulo de caja autoriza), para que vea datos reales
  // sin pedir analítica que no tiene (cero 401).
  const today = useQuery({
    queryKey: ['dashboard', 'today'],
    enabled: canSeeBi,
    queryFn: async () => (await api.get<DashboardSummary>('/bi/dashboard/today')).data,
  });
  const myShift = useQuery({
    queryKey: ['shift', 'my-summary'],
    enabled: !canSeeBi,
    queryFn: async () => {
      const res = await api.get<ShiftSummary>('/shifts/my-summary');
      return res.status === 204 ? null : res.data;
    },
  });
  // La cartera por cobrar es del módulo clientes; solo se pide si el rol lo tiene.
  const receivables = useQuery({
    queryKey: ['receivables-summary'],
    enabled: canSeeCustomers,
    queryFn: async () => (await api.get<Summary>('/customers/receivables/summary')).data,
  });

  const salesTotal = canSeeBi ? (today.data?.salesTotal ?? 0) : (myShift.data?.totalSales ?? 0);
  const salesCount = canSeeBi ? (today.data?.salesCount ?? 0) : (myShift.data?.salesCount ?? 0);
  const avgTicket = canSeeBi
    ? (salesCount > 0 ? salesTotal / salesCount : 0)
    : (myShift.data?.averageTicket ?? 0);

  const stats: ModuleStat[] = [
    { label: 'Ventas de hoy', value: money(salesTotal), icon: <DollarSign size={20} /> },
    { label: 'Tickets de hoy', value: int(salesCount), icon: <Receipt size={20} /> },
    { label: 'Ticket promedio', value: money(avgTicket), icon: <TrendingUp size={20} /> },
    { label: 'Por cobrar', value: money(receivables.data?.totalPending ?? 0), icon: <HandCoins size={20} /> },
  ];

  const options: ModuleOption[] = [
    { to: '/pos', label: 'Punto de venta', sub: 'Cobra y genera tickets', icon: <ShoppingCart size={26} />, from: '#16a34a', to2: '#22c55e' },
    { to: '/returns', label: 'Devoluciones', sub: 'Devuelve productos y reembolsa', icon: <Undo2 size={26} />, from: '#e11d48', to2: '#f43f5e' },
    { to: '/promotions', label: 'Promociones', sub: '2x1, %, precios especiales', icon: <Tag size={26} />, from: '#d97706', to2: '#f59e0b' },
    { to: '/advances', label: 'Anticipos', sub: 'Dinero a cuenta de clientes', icon: <Wallet size={26} />, from: '#7c3aed', to2: '#8b5cf6' },
    { to: '/collections', label: 'Cobranza', sub: 'Cuentas por cobrar y abonos', icon: <HandCoins size={26} />, from: '#0891b2', to2: '#06b6d4' },
    { to: '/cashier', label: 'Cortes de caja', sub: 'Turnos, arqueos y cortes X/Z', icon: <Receipt size={26} />, from: '#c026d3', to2: '#d946ef' },
  ];

  return (
    <ModuleLanding
      title="Ventas"
      subtitle="Punto de venta, devoluciones, promociones, anticipos, cobranza y cortes"
      accentFrom="#15803d"
      accentTo="#22c55e"
      stats={stats}
      options={options}
    />
  );
}
