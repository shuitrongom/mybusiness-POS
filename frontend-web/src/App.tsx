import { Navigate, Route, Routes } from 'react-router-dom';
import { useSession } from '@/store/session';
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

/**
 * Componente raíz: define las rutas. Las rutas protegidas requieren sesión iniciada;
 * si no hay sesión, redirige al login.
 */
export function App() {
  const isAuthenticated = useSession((s) => s.isAuthenticated);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={isAuthenticated ? <AppLayout /> : <Navigate to="/login" replace />}
      >
        <Route index element={<DashboardPage />} />
        <Route path="pos" element={<PosPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="customers" element={<CustomersPage />} />
        <Route path="inventory" element={<InventoryPage />} />
        <Route path="purchasing" element={<PurchasingPage />} />
        <Route path="cashier" element={<CashierPage />} />
        <Route path="payments" element={<PaymentsPage />} />
        <Route path="invoicing" element={<InvoicingPage />} />
        <Route path="admin" element={<SuperAdminPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
