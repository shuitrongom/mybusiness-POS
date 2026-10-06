import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  ShoppingCart, ShoppingBag, Boxes, Wallet, FileText, Settings,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { api } from '@/lib/api';
import { useSession } from '@/store/session';
import { decodeToken, canUseModule } from '@/lib/jwt';
import { staggerContainer, staggerItem } from '@/lib/motion';
import './home.css';

interface DashboardSummary { salesCount: number; salesTotal: number; itemsSold: number; }
interface StockLevel { productId: number; }

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

  const summary = useQuery({
    queryKey: ['dashboard', 'today'],
    queryFn: async () => (await api.get<DashboardSummary>('/bi/dashboard/today')).data,
  });
  const lowStock = useQuery({
    queryKey: ['inventory', 'low-stock'],
    queryFn: async () => (await api.get<StockLevel[]>('/inventory/alerts/low-stock')).data,
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
