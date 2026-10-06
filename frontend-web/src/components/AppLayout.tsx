import { useState, type ReactNode } from 'react';
import { useEffect } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  Home, LayoutDashboard, ShoppingCart, ShoppingBag, Boxes,
  Settings, ChevronDown, Smartphone, FileText,
} from 'lucide-react';
import { ThemeToggle } from '@/components/ThemeToggle';
import { UserMenu } from '@/components/UserMenu';
import { useSession } from '@/store/session';
import { decodeToken, canUseModule, canSell } from '@/lib/jwt';
import { api } from '@/lib/api';
import { setPaperWidth } from '@/lib/thermalPrint';
import './app-layout.css';

interface AppLayoutProps {
  superAdmin: boolean;
  cashier?: boolean;
}

interface NavItem { to: string; label: string; end?: boolean; }
/** module: clave del módulo que rige el acceso al grupo (para filtrar por permisos del rol). */
interface NavGroup { id: string; label: string; icon: ReactNode; module: string; items: NavItem[]; }

/**
 * Grupos del menú del Dueño/Administrador. Cada grupo es un submenú colapsable, para que el menú
 * lateral no crezca de más: solo se ven los módulos y se expande el que se usa. El orden refleja
 * el orden de los módulos en el Inicio.
 */
const OWNER_GROUPS: NavGroup[] = [
  {
    id: 'sales', label: 'Ventas', icon: <ShoppingCart size={17} />, module: 'sales',
    items: [
      { to: '/pos', label: 'Punto de venta' },
      { to: '/returns', label: 'Devoluciones' },
      { to: '/promotions', label: 'Promociones' },
      { to: '/advances', label: 'Anticipos' },
      { to: '/collections', label: 'Cobranza' },
      { to: '/customers', label: 'Clientes' },
      { to: '/cashier', label: 'Cortes de caja' },
      { to: '/sales-by-cashier', label: 'Ventas por cajero' },
      { to: '/ticket-reprint', label: 'Reimpresión de tickets' },
    ],
  },
  {
    id: 'purchasing', label: 'Compras', icon: <ShoppingBag size={17} />, module: 'purchasing',
    items: [
      { to: '/purchases', label: 'Compras' },
      { to: '/purchase-orders', label: 'Órdenes de compra' },
      { to: '/purchase-returns', label: 'Devolución de compras' },
      { to: '/suppliers', label: 'Proveedores' },
      { to: '/payables', label: 'Cuentas por pagar' },
      { to: '/supplier-visits', label: 'Visita de proveedores' },
      { to: '/purchase-xml', label: 'Compras XML' },
    ],
  },
  {
    id: 'inventory', label: 'Inventario', icon: <Boxes size={17} />, module: 'inventory',
    items: [
      { to: '/inventory', label: 'Existencias' },
      { to: '/products', label: 'Catálogo de artículos' },
      { to: '/inventory-quick', label: 'Alta rápida' },
      { to: '/inventory-variants', label: 'Tallas y colores' },
      { to: '/inventory-entries', label: 'Entradas' },
      { to: '/inventory-exits', label: 'Salidas' },
      { to: '/inventory-transfers', label: 'Traspasos' },
      { to: '/inventory-physical', label: 'Inventario físico' },
      { to: '/inventory-serials', label: 'Números de serie' },
      { to: '/inventory-lots', label: 'Números de lote' },
      { to: '/inventory-quality', label: 'Calidad de inventario' },
      { to: '/inventory-labels', label: 'Etiquetas de barras' },
      { to: '/inventory-stockapp', label: 'StockApp' },
      { to: '/inventory-kardex', label: 'Kardex' },
    ],
  },
  {
    id: 'invoicing', label: 'Facturación electrónica', icon: <FileText size={17} />, module: 'invoicing',
    items: [
      { to: '/facturacion', label: 'Panel de facturación' },
      { to: '/invoicing-issuer', label: 'Datos para factura' },
      { to: '/invoicing-list', label: 'Lista de facturas' },
      { to: '/invoicing-receipt', label: 'Generar recibos de pago' },
      { to: '/invoicing-receipts', label: 'Consultar recibos de pago' },
      { to: '/invoicing-carta-porte', label: 'Catálogos Carta Porte' },
      { to: '/invoicing-remissions', label: 'Remisiones a factura' },
      { to: '/invoicing-tickets', label: 'Tickets a factura' },
      { to: '/invoicing-closing', label: 'Factura de cierre' },
      { to: '/invoicing-sat-products', label: 'Catálogo SAT Productos' },
      { to: '/invoicing-sat-lines', label: 'Catálogo SAT Líneas' },
    ],
  },
  {
    id: 'payments', label: 'Recargas y servicios', icon: <Smartphone size={17} />, module: 'payments',
    items: [
      { to: '/pagos', label: 'Panel de pagos' },
      { to: '/pay-recharge', label: 'Vender recarga' },
      { to: '/pay-service', label: 'Pago de servicios' },
      { to: '/pay-deposit', label: 'Reportar abono' },
      { to: '/pay-deposits', label: 'Consultar abonos' },
      { to: '/pay-operations', label: 'Operaciones' },
      { to: '/pay-balance', label: 'Consultar saldo' },
    ],
  },
  {
    id: 'admin', label: 'Administración', icon: <Settings size={17} />, module: 'settings',
    items: [
      { to: '/branches', label: 'Sucursales' },
      { to: '/users', label: 'Usuarios' },
      { to: '/roles', label: 'Roles y permisos' },
      { to: '/documents', label: 'Comprobantes y cortes' },
      { to: '/ticket-reprint', label: 'Reimpresión de tickets' },
      { to: '/ticket-settings', label: 'Configuración de ticket' },
    ],
  },
];

/**
 * Estructura principal de la aplicación autenticada. El menú se adapta al rol:
 * - Super Admin: panel del SaaS y administración.
 * - Cajero: solo el punto de venta.
 * - Dueño / Administrador: menú por módulos con submenús colapsables.
 */
export function AppLayout({ superAdmin, cashier = false }: AppLayoutProps) {
  const roleTag = superAdmin ? 'ADMINISTRACIÓN' : cashier ? 'CAJA' : 'CLOUD POS';
  const location = useLocation();
  const token = useSession((s) => s.token);
  const claims = decodeToken(token);

  // Grupos visibles según los permisos del rol: el usuario ve un grupo si su rol tiene algún
  // permiso sobre el módulo del grupo. Dueño/Admin ven todo. Así el cajero deja de estar
  // encajonado en el POS y ve exactamente lo que su rol permite.
  const maySell = canSell(claims);
  const visibleGroups = OWNER_GROUPS
    .filter((g) => canUseModule(claims, g.module))
    .map((g) => g.id === 'sales' && !maySell
      // Administración/supervisión no vende: se oculta "Punto de venta", pero sí ven el resto
      // de Ventas (devoluciones, clientes, cobranza, cortes, ventas por cajero, reimpresión).
      ? { ...g, items: g.items.filter((it) => it.to !== '/pos') }
      : g);

  // El grupo cuya ruta está activa arranca expandido; los demás, contraídos.
  const activeGroup = visibleGroups.find((g) => g.items.some((it) => location.pathname.startsWith(it.to)))?.id;
  const [open, setOpen] = useState<string | null>(activeGroup ?? visibleGroups[0]?.id ?? null);

  const toggle = (id: string) => setOpen((cur) => (cur === id ? null : id));

  // Carga el ancho de papel configurado (58/80) una vez, para que toda impresión térmica
  // (tickets y comprobantes) use el tamaño correcto en cualquier pantalla.
  useEffect(() => {
    if (superAdmin) return;
    api.get<{ paperWidthMm: number }>('/tickets/settings')
      .then((r) => setPaperWidth(r.data.paperWidthMm))
      .catch(() => {});
  }, [superAdmin]);

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">MS</div>
          <div className="brand-text">
            <span className="brand-name">MyBusiness Silva</span>
            <span className="brand-tag">{roleTag}</span>
          </div>
        </div>

        <nav className="nav">
          {superAdmin ? (
            <>
              <NavLink to="/" end className="nav-link"><LayoutDashboard size={17} /> Panel del sistema</NavLink>
              <NavLink to="/admin" className="nav-link"><Settings size={17} /> Negocios y planes</NavLink>
            </>
          ) : (
            <>
              <NavLink to="/" end className="nav-link"><Home size={17} /> Inicio</NavLink>
              <NavLink to="/dashboard" className="nav-link"><LayoutDashboard size={17} /> Resumen</NavLink>

              {visibleGroups.map((g) => {
                const isOpen = open === g.id;
                const hasActive = g.items.some((it) => location.pathname.startsWith(it.to));
                return (
                  <div key={g.id} className="nav-acc">
                    <button
                      className={`nav-acc-head ${hasActive ? 'has-active' : ''}`}
                      onClick={() => toggle(g.id)}
                      aria-expanded={isOpen}
                    >
                      <span className="nav-acc-title">{g.icon} {g.label}</span>
                      <ChevronDown size={15} className={`nav-acc-chev ${isOpen ? 'is-open' : ''}`} />
                    </button>
                    <div className={`nav-acc-body ${isOpen ? 'is-open' : ''}`}>
                      {g.items.map((it) => (
                        <NavLink key={it.to} to={it.to} end={it.end} className="nav-sublink">
                          {it.label}
                        </NavLink>
                      ))}
                    </div>
                  </div>
                );
              })}
            </>
          )}
        </nav>
      </aside>

      <main className="content">
        <header className="topbar">
          <div className="topbar-spacer" />
          <div className="topbar-actions">
            <ThemeToggle />
            <UserMenu />
          </div>
        </header>
        <div className="content-body">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
