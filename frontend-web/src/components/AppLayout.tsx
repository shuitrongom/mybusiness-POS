import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useSession } from '@/store/session';
import './app-layout.css';

/**
 * Estructura principal de la aplicación autenticada: barra lateral de navegación con la marca
 * y el contenido de la ruta activa. Diseño enterprise, responsivo (la barra se colapsa en móvil).
 */
export function AppLayout() {
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
            <span className="brand-tag">CLOUD POS</span>
          </div>
        </div>

        <nav className="nav">
          <NavLink to="/" end className="nav-link">Panel</NavLink>
          <NavLink to="/pos" className="nav-link">Punto de venta</NavLink>
          <NavLink to="/products" className="nav-link">Productos</NavLink>
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
