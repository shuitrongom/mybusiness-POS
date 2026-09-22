import { Navigate, Route, Routes } from 'react-router-dom';
import { useSession } from '@/store/session';
import { decodeToken, isSuperAdmin } from '@/lib/jwt';
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
import { SuperAdminPage } from '@/pages/SuperAdminPage';
import { SuperAdminDashboardPage } from '@/pages/SuperAdminDashboardPage';

/**
 * Componente raíz: define las rutas según el rol del usuario.
 *
 * - Super Admin (proveedor del SaaS): solo su panel de BI del SaaS y la administración de
 *   negocios/planes. NO ve la operación de los negocios (POS, ventas, inventario, etc.).
 * - Usuario de negocio (dueño/cajero): la operación completa del punto de venta.
 */
export function App() {
  const token = useSession((s) => s.token);
  const isAuthenticated = useSession((s) => s.isAuthenticated);
  const claims = decodeToken(token);
  const superAdmin = isSuperAdmin(claims);

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
      <Route path="/" element={<AppLayout superAdmin={superAdmin} />}>
        {superAdmin ? (
          <>
            {/* Super Admin: panel del SaaS + administración */}
            <Route index element={<SuperAdminDashboardPage />} />
            <Route path="admin" element={<SuperAdminPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </>
        ) : (
          <>
            {/* Usuario de negocio: operación del POS */}
            <Route index element={<DashboardPage />} />
            <Route path="pos" element={<PosPage />} />
            <Route path="products" element={<ProductsPage />} />
            <Route path="inventory" element={<InventoryPage />} />
            <Route path="purchasing" element={<PurchasingPage />} />
            <Route path="customers" element={<CustomersPage />} />
            <Route path="cashier" element={<CashierPage />} />
            <Route path="payments" element={<PaymentsPage />} />
            <Route path="invoicing" element={<InvoicingPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </>
        )}
      </Route>
    </Routes>
  );
}
