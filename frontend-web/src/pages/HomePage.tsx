import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  ShoppingCart, ShoppingBag, Boxes, Wallet, FileText, Settings,
  Banknote, Receipt, Package, TrendingUp, Clock, User, Sparkles, BadgeCheck,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { api } from '@/lib/api';
import { useSession } from '@/store/session';
import { decodeToken, canUseModule } from '@/lib/jwt';
import { staggerContainer, staggerItem } from '@/lib/motion';
import { toast } from '@/store/toast';
import { OpenShiftModal, cacheShift, type ActiveShift } from '@/components/OpenShiftModal';
import './home.css';

interface DashboardSummary { salesCount: number; salesTotal: number; itemsSold: number; }
interface StockLevel { productId: number; }

/** Resumen en vivo del turno del cajero (GET /shifts/my-summary). 204 si no tiene caja abierta. */
interface ShiftSummary {
  registerName: string | null;
  totalSales: number;
  salesCount: number;
  units: number;
  averageTicket: number;
  expectedCash: number;
  cashSales: number;
  topProducts: { name: string; quantity: number; revenue: number }[];
  lastSales: { id: number; folio: string; total: number; createdAt: string }[];
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

interface Tile {
  to: string;
  label: string;
  sub: string;
  icon: ReactNode;
  from: string;
  to2: string;
  live?: string;
  module: string;
}

/**
 * Home tipo mosaico premium: acceso a todos los módulos con tarjetas grandes (degradado + icono),
 * cada una con un dato en vivo cuando aplica. Supera al menú plano de MyBusiness con jerarquía,
 * color de marca por módulo, información contextual y animación.
 */
export function HomePage() {
  const navigate = useNavigate();
  const token = useSession((s) => s.token);
  const claims = decodeToken(token);

  // El Inicio del DUEÑO/ADMIN muestra los números globales del negocio (ventas del día y alertas
  // de stock), que viven en los módulos 'bi' e 'inventory'. El CAJERO no tiene esos módulos: para
  // él estas consultas NO se disparan (evita 401 de raíz) y en su lugar ve su propio resumen de
  // turno (hero de cajero, más abajo). Así cada rol pide solo lo que su permiso autoriza.
  const canSeeBi = canUseModule(claims, 'bi');
  const canSeeInventory = canUseModule(claims, 'inventory');

  const summary = useQuery({
    queryKey: ['dashboard', 'today'],
    enabled: canSeeBi,
    queryFn: async () => (await api.get<DashboardSummary>('/bi/dashboard/today')).data,
  });
  const lowStock = useQuery({
    queryKey: ['inventory', 'low-stock'],
    enabled: canSeeInventory,
    queryFn: async () => (await api.get<StockLevel[]>('/inventory/alerts/low-stock')).data,
  });

  // Es cajero operativo (vende y maneja su caja, pero NO ve la analítica del negocio). Para él, el
  // Inicio es su propio tablero de turno. Para dueño/admin, el panel de módulos de siempre.
  const isCashier = canUseModule(claims, 'cash') && !canSeeBi;

  // Resumen EN VIVO del turno del cajero. 204 (sin caja abierta) => data undefined, se muestra el
  // estado "abre tu caja". Solo se consulta para el cajero (su módulo 'cash' lo autoriza).
  const myShift = useQuery({
    queryKey: ['shift', 'my-summary'],
    enabled: isCashier,
    queryFn: async () => {
      const res = await api.get<ShiftSummary>('/shifts/my-summary');
      return res.status === 204 ? null : res.data;
    },
  });

  // Estado del día del cajero: si ya cerró su caja hoy NO puede volver a abrir (misma regla que el
  // POS). Se usa para que el CTA "Abrir mi caja" avise en vez de abrir el modal. 'cash' lo autoriza.
  const dayStatus = useQuery({
    queryKey: ['shift', 'day-status'],
    enabled: isCashier,
    queryFn: async () => (await api.get<{ closedToday: boolean }>('/shifts/day-status')).data,
  });

  const salesLive = summary.data ? `${money(summary.data.salesTotal)} hoy` : undefined;
  const stockLive = lowStock.data ? `${int(lowStock.data.length)} alertas` : undefined;

  // Módulos del negocio en el Inicio (mismos que los grupos del menú lateral). Cada tile lleva al
  // panel del módulo, desde donde se accede a sus submódulos.
  const allTiles: Tile[] = [
    { to: '/ventas', label: 'Ventas', sub: 'Punto de venta, devoluciones, clientes, cobranza y cortes', icon: <ShoppingCart size={30} color="#fff" />, from: '#16a34a', to2: '#22c55e', live: salesLive, module: 'sales' },
    { to: '/compras', label: 'Compras', sub: 'Órdenes, proveedores, cuentas por pagar y CFDI', icon: <ShoppingBag size={30} color="#fff" />, from: '#ea580c', to2: '#f97316', module: 'purchasing' },
    { to: '/inventario', label: 'Inventario', sub: 'Existencias, productos, series, lotes y etiquetas', icon: <Boxes size={30} color="#fff" />, from: '#0891b2', to2: '#06b6d4', live: stockLive, module: 'inventory' },
    { to: '/facturacion', label: 'Facturación electrónica', sub: 'CFDI 4.0, pagos, carta porte y catálogos SAT', icon: <FileText size={30} color="#fff" />, from: '#0d9488', to2: '#14b8a6', module: 'invoicing' },
    { to: '/pagos', label: 'Recargas y servicios', sub: 'Tiempo aire, servicios, saldo y comisiones', icon: <Wallet size={30} color="#fff" />, from: '#9333ea', to2: '#a855f7', module: 'payments' },
    { to: '/branches', label: 'Administración', sub: 'Sucursales, usuarios, tickets y configuración', icon: <Settings size={30} color="#fff" />, from: '#475569', to2: '#64748b', module: 'settings' },
  ];
  // Solo se muestran los módulos que el rol del usuario puede usar (igual que el menú lateral).
  const tiles = allTiles.filter((t) => canUseModule(claims, t.module));

  // CAJERO: su propio tablero de turno premium (sus números, sus accesos). Sin analítica del
  // negocio ni llamadas a módulos que no tiene (cero 401).
  if (isCashier) {
    const cajero = claims?.subject ?? 'Cajero';
    return (
      <CashierHome
        cajero={cajero}
        shift={myShift.data ?? null}
        loading={myShift.isLoading}
        closedToday={dayStatus.data?.closedToday ?? false}
        tiles={tiles}
        onGo={navigate}
      />
    );
  }

  // DUEÑO / ADMIN: panel de módulos del negocio con datos globales en vivo.
  return (
    <div>
      <div className="home-head">
        <div>
          <h1 className="page-title">Panel de control</h1>
          <p className="page-sub">Todos los módulos de tu negocio en un solo lugar</p>
        </div>
      </div>

      <motion.div className="home-grid" variants={staggerContainer} initial="hidden" animate="visible">
        {tiles.map((t) => (
          <motion.button key={t.to} className="home-tile" variants={staggerItem}
            onClick={() => navigate(t.to)}
            whileHover={{ y: -4, scale: 1.02 }} whileTap={{ scale: 0.98 }}
            style={{ background: `linear-gradient(135deg, ${t.from}, ${t.to2})` }}>
            <div className="home-tile-glow" />
            <div className="home-tile-icon">{t.icon}</div>
            <div className="home-tile-text">
              <span className="home-tile-label">{t.label}</span>
              <span className="home-tile-sub">{t.sub}</span>
            </div>
            {t.live && <span className="home-tile-live">{t.live}</span>}
          </motion.button>
        ))}
      </motion.div>
    </div>
  );
}

/**
 * Inicio del CAJERO: tablero premium de su turno. Muestra, en vivo, lo que SÍ puede ver con su
 * módulo de caja (ventas de hoy, efectivo esperado, tickets, ticket promedio, lo más vendido y
 * sus últimos tickets), más accesos grandes a sus herramientas. Si no tiene caja abierta, invita
 * a abrirla. Nunca pide analítica del negocio (nada de /bi), por lo que no genera 401.
 */
function CashierHome({ cajero, shift, loading, closedToday, tiles, onGo }: {
  cajero: string;
  shift: ShiftSummary | null;
  loading: boolean;
  closedToday: boolean;
  tiles: Tile[];
  onGo: (to: string) => void;
}) {
  const navigate = useNavigate();
  // Modal premium de apertura de caja (el MISMO del POS). Al abrir con éxito, se cachea el turno y
  // se entra al punto de venta, igual que el flujo de apertura desde el POS.
  const [openModal, setOpenModal] = useState(false);
  // Sucursal del cajero: mismo criterio que el POS (localStorage 'mbs.branchId', respaldo 1).
  const branchId = Number(localStorage.getItem('mbs.branchId')) || 1;
  const abierta = shift != null;
  const saludo = new Date().getHours() < 12 ? 'Buenos días'
    : new Date().getHours() < 19 ? 'Buenas tardes' : 'Buenas noches';

  // CTA "Abrir mi caja": si el cajero ya cerró su corte hoy NO puede reabrir (regla de negocio
  // validada también en backend); se avisa y NO se abre el modal. Si puede, se abre el modal
  // premium de apertura.
  const onAbrirCaja = () => {
    if (closedToday) {
      toast.info('Tu corte de hoy ya fue cerrado', 'Podrás abrir caja de nuevo hasta el día siguiente.');
      return;
    }
    setOpenModal(true);
  };

  // Al confirmar la apertura: cachea el turno recién abierto (para que el POS lo reconozca sin
  // volver a pedir apertura) y entra al punto de venta, idéntico a la apertura desde el POS.
  const onOpened = (s: ActiveShift) => {
    cacheShift(s);
    navigate('/pos');
  };

  const metrics = abierta ? [
    { icon: <TrendingUp size={20} />, label: 'Ventas de hoy', value: money(shift.totalSales), accent: '#12b886' },
    { icon: <Banknote size={20} />, label: 'Efectivo en caja', value: money(shift.expectedCash), accent: '#2563eb' },
    { icon: <Receipt size={20} />, label: 'Tickets', value: int(shift.salesCount), accent: '#7c5cff' },
    { icon: <Package size={20} />, label: 'Ticket promedio', value: money(shift.averageTicket), accent: '#d97706' },
  ] : [];

  return (
    <div className="cajero-home">
      {/* Hero de bienvenida */}
      <motion.div className="cajero-hero" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 240, damping: 24 }}>
        <div className="cajero-hero-left">
          <span className="cajero-hero-badge"><Sparkles size={13} /> Tu turno</span>
          <h1 className="cajero-hero-title">{saludo}, {cajero}</h1>
          <p className="cajero-hero-sub">
            {abierta
              ? <>Caja <strong>{shift.registerName ?? 'abierta'}</strong> · resumen en vivo de tu día</>
              : 'Abre tu caja para empezar a vender y ver tu resumen del día.'}
          </p>
        </div>
        <div className="cajero-hero-icon"><User size={30} /></div>
      </motion.div>

      {/* Métricas del turno */}
      {loading && <div className="cajero-loading">Cargando tu turno…</div>}

      {!loading && abierta && (
        <motion.div className="cajero-metrics" variants={staggerContainer} initial="hidden" animate="visible">
          {metrics.map((m) => (
            <motion.div key={m.label} className="cajero-metric" variants={staggerItem}>
              <span className="cajero-metric-icon" style={{ color: m.accent, background: `color-mix(in srgb, ${m.accent} 14%, transparent)` }}>
                {m.icon}
              </span>
              <div className="cajero-metric-text">
                <span className="cajero-metric-value">{m.value}</span>
                <span className="cajero-metric-label">{m.label}</span>
              </div>
            </motion.div>
          ))}
        </motion.div>
      )}

      {!loading && !abierta && !closedToday && (
        <motion.button className="cajero-open-cta" onClick={onAbrirCaja}
          initial={{ opacity: 0 }} animate={{ opacity: 1 }}
          whileHover={{ y: -3 }} whileTap={{ scale: 0.98 }}>
          <Banknote size={26} />
          <div>
            <strong>Abrir mi caja</strong>
            <span>Registra tu fondo inicial para comenzar a vender</span>
          </div>
        </motion.button>
      )}

      {/* Ya cerró su corte hoy: no puede reabrir caja (regla de negocio). Estado informativo. */}
      {!loading && !abierta && closedToday && (
        <motion.div className="cajero-open-cta cajero-open-cta-done" aria-disabled
          initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
          <BadgeCheck size={26} />
          <div>
            <strong>Tu caja de hoy ya cerró</strong>
            <span>Tu corte ya fue cerrado; podrás abrir caja de nuevo hasta el día siguiente.</span>
          </div>
        </motion.div>
      )}

      {/* Modal premium de apertura (el MISMO del POS). Al confirmar, entra al punto de venta. */}
      <OpenShiftModal open={openModal} branchId={branchId} onOpened={onOpened} />

      {/* Accesos del cajero */}
      <div className="cajero-section-title">Tus herramientas</div>
      <motion.div className="cajero-tiles" variants={staggerContainer} initial="hidden" animate="visible">
        {tiles.map((t) => (
          <motion.button key={t.to} className="cajero-tile" variants={staggerItem}
            onClick={() => onGo(t.to)}
            whileHover={{ y: -4, scale: 1.02 }} whileTap={{ scale: 0.98 }}
            style={{ background: `linear-gradient(135deg, ${t.from}, ${t.to2})` }}>
            <div className="home-tile-glow" />
            <div className="home-tile-icon">{t.icon}</div>
            <div className="home-tile-text">
              <span className="home-tile-label">{t.label}</span>
              <span className="home-tile-sub">{t.sub}</span>
            </div>
          </motion.button>
        ))}
      </motion.div>

      {/* Lo más vendido y últimos tickets de su turno */}
      {abierta && (shift.topProducts.length > 0 || shift.lastSales.length > 0) && (
        <div className="cajero-lists">
          {shift.topProducts.length > 0 && (
            <div className="cajero-card">
              <div className="cajero-card-head"><TrendingUp size={16} /> Lo más vendido hoy</div>
              <ul className="cajero-list">
                {shift.topProducts.map((p, i) => (
                  <li key={i}>
                    <span className="cajero-list-rank">{i + 1}</span>
                    <span className="cajero-list-name">{p.name}</span>
                    <span className="cajero-list-qty">{int(p.quantity)}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {shift.lastSales.length > 0 && (
            <div className="cajero-card">
              <div className="cajero-card-head"><Clock size={16} /> Tus últimos tickets</div>
              <ul className="cajero-list">
                {shift.lastSales.map((s) => (
                  <li key={s.id}>
                    <span className="cajero-list-folio">{s.folio}</span>
                    <span className="cajero-list-total">{money(s.total)}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
