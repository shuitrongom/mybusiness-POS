import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useSession } from '@/store/session';
import './app-layout.css';

interface AppLayoutProps {
  superAdmin: boolean;
}

/**
 * Estructura principal de la aplicación autenticada. El menú se adapta al rol:
 * - Super Admin: panel del SaaS y administración (nada de operación de negocios).
 * - Usuario de negocio: operación completa del punto de venta.
 */
export function AppLayout({ superAdmin }: AppLayoutProps) {
  const logout = useSession((s) => s.logout);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">MS</div>
          <div className="brand-text">
            <span className="brand-name">MyBusiness Silva</span>
            <span className="brand-tag">{superAdmin ? 'ADMINISTRACIÓN' : 'CLOUD POS'}</span>
          </div>
        </div>

        <nav className="nav">
          {superAdmin ? (
            <>
              <NavLink to="/" end className="nav-link">Panel del sistema</NavLink>
              <NavLink to="/admin" className="nav-link">Negocios y planes</NavLink>
            </>
          ) : (
            <>
              <NavLink to="/" end className="nav-link">Panel</NavLink>
              <NavLink to="/pos" className="nav-link">Punto de venta</NavLink>
              <NavLink to="/products" className="nav-link">Productos</NavLink>
              <NavLink to="/inventory" className="nav-link">Inventario</NavLink>
              <NavLink to="/purchasing" className="nav-link">Compras</NavLink>
              <NavLink to="/customers" className="nav-link">Clientes</NavLink>
              <NavLink to="/cashier" className="nav-link">Cortes de caja</NavLink>
              <NavLink to="/payments" className="nav-link">Recargas</NavLink>
              <NavLink to="/invoicing" className="nav-link">Facturación</NavLink>
            </>
          )}
        </nav>

        <button className="btn-ghost logout" onClick={handleLogout}>
          Cerrar sesión
        </button>
      </aside>

      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
