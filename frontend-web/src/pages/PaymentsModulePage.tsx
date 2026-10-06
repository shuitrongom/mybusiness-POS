import { useQuery } from '@tanstack/react-query';
import {
  Smartphone, Zap, Landmark, ListChecks, Wallet, BadgeDollarSign,
  Receipt, TriangleAlert,
} from 'lucide-react';
import { api } from '@/lib/api';
import { ModuleLanding, type ModuleOption, type ModuleStat } from '@/components/ModuleLanding';

interface Summary {
  balance: number; commissions: number; todayOps: number;
  todayAmount: number; pendingDeposits: number;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

/**
 * Página del módulo RECARGAS Y PAGO DE SERVICIOS: resumen (saldo, comisiones, operaciones de hoy,
 * abonos pendientes) y opciones de la suite. Modelo de corresponsalía prepago.
 */
export function PaymentsModulePage() {
  const summary = useQuery({
    queryKey: ['payments', 'balance'],
    queryFn: async () => (await api.get<Summary>('/payments/balance')).data,
  });
  const s = summary.data;

  const stats: ModuleStat[] = [
    { label: 'Saldo disponible', value: money(s?.balance ?? 0), icon: <Wallet size={20} /> },
    { label: 'Comisiones ganadas', value: money(s?.commissions ?? 0), icon: <BadgeDollarSign size={20} />, tone: 'success' },
    { label: 'Operaciones de hoy', value: int(s?.todayOps ?? 0), icon: <Receipt size={20} /> },
    { label: 'Abonos por aprobar', value: int(s?.pendingDeposits ?? 0), icon: <TriangleAlert size={20} />, tone: (s?.pendingDeposits ?? 0) > 0 ? 'warning' : 'accent' },
  ];

  const options: ModuleOption[] = [
    { to: '/pay-recharge', label: 'Vender recarga', sub: 'Tiempo aire de todas las compañías', icon: <Smartphone size={26} />, from: '#7c3aed', to2: '#a855f7' },
    { to: '/pay-service', label: 'Pago de servicios', sub: 'Luz, agua, gas, gobierno y más', icon: <Zap size={26} />, from: '#ea580c', to2: '#f97316' },
    { to: '/pay-deposit', label: 'Reportar abono', sub: 'Reporta tu depósito para reponer saldo', icon: <Landmark size={26} />, from: '#16a34a', to2: '#22c55e' },
    { to: '/pay-deposits', label: 'Consultar abonos', sub: 'Depósitos reportados y su estado', icon: <ListChecks size={26} />, from: '#0891b2', to2: '#06b6d4' },
    { to: '/pay-operations', label: 'Operaciones realizadas', sub: 'Historial de recargas y pagos', icon: <Receipt size={26} />, from: '#2563eb', to2: '#3b82f6' },
    { to: '/pay-balance', label: 'Consultar saldo', sub: 'Saldo disponible y movimientos', icon: <Wallet size={26} />, from: '#0d9488', to2: '#14b8a6' },
  ];

  return (
    <ModuleLanding
      title="Recargas y pago de servicios"
      subtitle="Tiempo aire de todas las compañías de México, pago de servicios, saldo prepagado, abonos y comisiones"
      accentFrom="#6d28d9"
      accentTo="#a855f7"
      stats={stats}
      options={options}
    />
  );
}
