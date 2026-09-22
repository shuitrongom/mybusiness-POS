import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useSession } from '@/store/session';
import './app-layout.css';

interface AppLayoutProps {
  superAdmin: boolean;
  cashier?: boolean;
}

/**
 * Estructura principal de la aplicación autenticada. El menú se adapta al rol:
 * - Super Admin: panel del SaaS y administración (nada de operación de negocios).
 * - Cajero: solo el punto de venta.
 * - Dueño / Administrador: operación completa + gestión de usuarios.
 */
export function AppLayout({ superAdmin, cashier = false }: AppLayoutProps) {
  const logout = useSession((s) => s.logout);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const roleTag = superAdmin ? 'ADMINISTRACIÓN' : cashier ? 'CAJA' : 'CLOUD POS';

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
              <NavLink to="/" end className="nav-link">Panel del sistema</NavLink>
              <NavLink to="/admin" className="nav-link">Negocios y planes</NavLink>
            </>
          ) : cashier ? (
            <>
              <NavLink to="/pos" className="nav-link">Punto de venta</NavLink>
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
              <NavLink to="/branches" className="nav-link">Sucursales</NavLink>
              <NavLink to="/users" className="nav-link">Usuarios</NavLink>
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
