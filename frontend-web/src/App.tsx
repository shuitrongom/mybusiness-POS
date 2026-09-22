import { Navigate, Route, Routes } from 'react-router-dom';
import { useSession } from '@/store/session';
import { decodeToken, isSuperAdmin, isCashier } from '@/lib/jwt';
import { LoginPage } from '@/pages/LoginPage';
import { AppLayout } from '@/components/AppLayout';
import { DashboardPage } from '@/pages/DashboardPage';
import { PosPage } from '@/pages/PosPage';
import { ProductsPage } from '@/pages/ProductsPage';
import { CustomersPage } from '@/pages/CustomersPage';
import { PaymentsPage } from '@/pages/PaymentsPage';
import { InvoicingPage } from '@/pages/InvoicingPage';
import { InventoryPage } from '@/pages/InventoryPage';
import { PurchasingPage } from '@/pages/PurchasingPage';
import { CashierPage } from '@/pages/CashierPage';
import { UsersPage } from '@/pages/UsersPage';
import { BranchesPage } from '@/pages/BranchesPage';
import { SuperAdminPage } from '@/pages/SuperAdminPage';
import { SuperAdminDashboardPage } from '@/pages/SuperAdminDashboardPage';

/**
 * Componente raíz: define las rutas según el rol del usuario.
 *
 * - Super Admin (proveedor del SaaS): panel del SaaS y administración de negocios/planes. No ve
 *   la operación de los negocios.
 * - Dueño / Administrador del negocio: operación completa + gestión de usuarios (cajeros).
 * - Cajero: solo el punto de venta.
 */
export function App() {
  const token = useSession((s) => s.token);
  const isAuthenticated = useSession((s) => s.isAuthenticated);
  const claims = decodeToken(token);
  const superAdmin = isSuperAdmin(claims);
  const cashier = isCashier(claims);

  if (!isAuthenticated) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/login" element={<Navigate to="/" replace />} />
      <Route path="/" element={<AppLayout superAdmin={superAdmin} cashier={cashier} />}>
        {superAdmin ? (
          <>
            {/* Super Admin: panel del SaaS + administración */}
            <Route index element={<SuperAdminDashboardPage />} />
            <Route path="admin" element={<SuperAdminPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </>
        ) : cashier ? (
          <>
            {/* Cajero: solo el punto de venta */}
            <Route index element={<PosPage />} />
            <Route path="pos" element={<PosPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </>
        ) : (
          <>
            {/* Dueño / Administrador: operación completa + usuarios */}
            <Route index element={<DashboardPage />} />
            <Route path="pos" element={<PosPage />} />
            <Route path="products" element={<ProductsPage />} />
            <Route path="inventory" element={<InventoryPage />} />
            <Route path="purchasing" element={<PurchasingPage />} />
            <Route path="customers" element={<CustomersPage />} />
            <Route path="cashier" element={<CashierPage />} />
            <Route path="branches" element={<BranchesPage />} />
            <Route path="users" element={<UsersPage />} />
            <Route path="payments" element={<PaymentsPage />} />
            <Route path="invoicing" element={<InvoicingPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </>
        )}
      </Route>
    </Routes>
  );
}
