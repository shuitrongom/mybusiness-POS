import { useQuery } from '@tanstack/react-query';
import {
  Building2, FileText, HandCoins, ReceiptText, Truck, FileStack,
  Ticket, FileCheck2, PackageSearch, Layers, ScrollText,
} from 'lucide-react';
import { api } from '@/lib/api';
import { ModuleLanding, type ModuleOption, type ModuleStat } from '@/components/ModuleLanding';

interface InvoiceRow { status: string; total: number; }

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

/**
 * Página del módulo FACTURACIÓN ELECTRÓNICA (CFDI 4.0): resumen (facturas del periodo, timbradas,
 * canceladas, monto facturado) y las opciones de la suite completa.
 */
export function InvoicingModulePage() {
  const invoices = useQuery({
    queryKey: ['invoicing', 'invoices', 'summary'],
    queryFn: async () => (await api.get<InvoiceRow[]>('/invoicing/invoices', { params: { limit: 500 } })).data,
  });
  const rows = invoices.data ?? [];
  const stamped = rows.filter((r) => r.status === 'STAMPED');
  const canceled = rows.filter((r) => r.status === 'CANCELED');
  const billed = stamped.reduce((a, r) => a + (Number(r.total) || 0), 0);

  const stats: ModuleStat[] = [
    { label: 'Comprobantes', value: int(rows.length), icon: <FileText size={20} /> },
    { label: 'Timbrados', value: int(stamped.length), icon: <FileCheck2 size={20} />, tone: 'success' },
    { label: 'Cancelados', value: int(canceled.length), icon: <FileStack size={20} />, tone: canceled.length > 0 ? 'warning' : 'accent' },
    { label: 'Facturado', value: money(billed), icon: <HandCoins size={20} />, tone: 'success' },
  ];

  const options: ModuleOption[] = [
    { to: '/invoicing-issuer', label: 'Datos para factura', sub: 'Datos fiscales del emisor y series', icon: <Building2 size={26} />, from: '#0f766e', to2: '#14b8a6' },
    { to: '/invoicing-list', label: 'Lista de facturas', sub: 'Consulta, PDF, cancelación y relacionados', icon: <FileText size={26} />, from: '#2563eb', to2: '#3b82f6' },
    { to: '/invoicing-receipt', label: 'Generar recibos de pago', sub: 'Complemento de Pagos 2.0', icon: <HandCoins size={26} />, from: '#16a34a', to2: '#22c55e' },
    { to: '/invoicing-receipts', label: 'Consultar recibos de pago', sub: 'Recibos emitidos y su estado', icon: <ReceiptText size={26} />, from: '#0891b2', to2: '#06b6d4' },
    { to: '/invoicing-carta-porte', label: 'Catálogos Carta Porte', sub: 'Permiso, vehículo, remolque, operador', icon: <Truck size={26} />, from: '#b45309', to2: '#f59e0b' },
    { to: '/invoicing-remissions', label: 'Remisiones a Factura', sub: 'Convierte remisiones en CFDI', icon: <FileStack size={26} />, from: '#7c3aed', to2: '#a855f7' },
    { to: '/invoicing-tickets', label: 'Tickets a Factura', sub: 'Convierte ventas en CFDI', icon: <Ticket size={26} />, from: '#db2777', to2: '#ec4899' },
    { to: '/invoicing-closing', label: 'Factura de Cierre', sub: 'Global del periodo (público en general)', icon: <FileCheck2 size={26} />, from: '#ea580c', to2: '#f97316' },
    { to: '/invoicing-sat-products', label: 'Catálogo SAT Productos', sub: 'Asigna clave SAT por artículo', icon: <PackageSearch size={26} />, from: '#4f46e5', to2: '#6366f1' },
    { to: '/invoicing-sat-lines', label: 'Catálogo SAT Líneas', sub: 'Asigna clave SAT por línea', icon: <Layers size={26} />, from: '#0369a1', to2: '#0ea5e9' },
    { to: '/invoicing', label: 'Emitir factura rápida', sub: 'Captura manual de un CFDI', icon: <ScrollText size={26} />, from: '#475569', to2: '#64748b' },
  ];

  return (
    <ModuleLanding
      title="Facturación Electrónica"
      subtitle="CFDI 4.0: facturas, complemento de pagos 2.0, carta porte, remisiones, factura de cierre y catálogos del SAT"
      accentFrom="#0d9488"
      accentTo="#14b8a6"
      stats={stats}
      options={options}
    />
  );
}
