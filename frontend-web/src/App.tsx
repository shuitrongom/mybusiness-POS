import { Navigate, Route, Routes } from 'react-router-dom';
import { useSession } from '@/store/session';
import { decodeToken, isSuperAdmin, isCashier } from '@/lib/jwt';
import { LoginPage } from '@/pages/LoginPage';
import { LandingPage } from '@/pages/LandingPage';
import { AppLayout } from '@/components/AppLayout';
import { HomePage } from '@/pages/HomePage';
import { DashboardPage } from '@/pages/DashboardPage';
import { PosPage } from '@/pages/PosPage';
import { ProductsPage } from '@/pages/ProductsPage';
import { CustomersPage } from '@/pages/CustomersPage';
import { SalesModulePage } from '@/pages/SalesModulePage';
import { ReturnsPage } from '@/pages/ReturnsPage';
import { PromotionsPage } from '@/pages/PromotionsPage';
import { AdvancesPage } from '@/pages/AdvancesPage';
import { CollectionsPage } from '@/pages/CollectionsPage';
import { PaymentsModulePage } from '@/pages/PaymentsModulePage';
import { PaymentRechargePage, PaymentServicePage } from '@/pages/PaymentSellPage';
import { PaymentDepositPage } from '@/pages/PaymentDepositPage';
import { PaymentDepositsPage } from '@/pages/PaymentDepositsPage';
import { PaymentOperationsPage } from '@/pages/PaymentOperationsPage';
import { PaymentBalancePage } from '@/pages/PaymentBalancePage';
import { TicketSettingsPage } from '@/pages/TicketSettingsPage';
import { TicketReprintPage } from '@/pages/TicketReprintPage';
import { InvoicingPage } from '@/pages/InvoicingPage';
import { InvoicingModulePage } from '@/pages/InvoicingModulePage';
import { InvoicingIssuerPage } from '@/pages/InvoicingIssuerPage';
import { InvoicingListPage } from '@/pages/InvoicingListPage';
import { InvoicingReceiptPage } from '@/pages/InvoicingReceiptPage';
import { InvoicingReceiptsPage } from '@/pages/InvoicingReceiptsPage';
import { InvoicingRemissionsPage, InvoicingTicketsPage } from '@/pages/InvoicingConvertPage';
import { InvoicingClosingPage } from '@/pages/InvoicingClosingPage';
import { InvoicingSatProductsPage } from '@/pages/InvoicingSatProductsPage';
import { InvoicingSatLinesPage } from '@/pages/InvoicingSatLinesPage';
import { InvoicingCartaPortePage } from '@/pages/InvoicingCartaPortePage';
import { InventoryPage } from '@/pages/InventoryPage';
import { InventoryModulePage } from '@/pages/InventoryModulePage';
import { InventoryQuickPage } from '@/pages/InventoryQuickPage';
import { InventoryVariantsPage } from '@/pages/InventoryVariantsPage';
import { InventoryEntriesPage, InventoryExitsPage } from '@/pages/InventoryDocPage';
import { InventorySerialsPage } from '@/pages/InventorySerialsPage';
import { InventoryLotsPage } from '@/pages/InventoryLotsPage';
import { InventoryTransfersPage } from '@/pages/InventoryTransfersPage';
import { InventoryQualityPage } from '@/pages/InventoryQualityPage';
import { InventoryLabelsPage } from '@/pages/InventoryLabelsPage';
import { InventoryPhysicalPage } from '@/pages/InventoryPhysicalPage';
import { InventoryStockAppPage } from '@/pages/InventoryStockAppPage';
import { InventoryKardexPage } from '@/pages/InventoryKardexPage';
import { PurchasingModulePage } from '@/pages/PurchasingModulePage';
import { PurchasesPage } from '@/pages/PurchasesPage';
import { PurchaseOrdersPage } from '@/pages/PurchaseOrdersPage';
import { PurchaseReturnsPage } from '@/pages/PurchaseReturnsPage';
import { SuppliersPage } from '@/pages/SuppliersPage';
import { PayablesPage } from '@/pages/PayablesPage';
import { SupplierVisitsPage } from '@/pages/SupplierVisitsPage';
import { PurchaseXmlPage } from '@/pages/PurchaseXmlPage';
import { CashierPage } from '@/pages/CashierPage';
import { UsersPage } from '@/pages/UsersPage';
import { RolesPage } from '@/pages/RolesPage';
import { SalesByCashierPage } from '@/pages/SalesByCashierPage';
import { CashierDashboardPage } from '@/pages/CashierDashboardPage';
import { DocumentsPage } from '@/pages/DocumentsPage';
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
        {/* Visitantes sin sesión: la landing pública es la página de inicio. */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        {/* Cualquier ruta desconocida vuelve a la landing (no al login). */}
        <Route path="*" element={<Navigate to="/" replace />} />
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
        ) : (
          <>
            {/* Usuarios del negocio (Dueño/Admin/Supervisor/Cajero): comparten el set de rutas;
                el acceso real lo acota el backend por permisos y el menú se filtra por rol. */}
            <Route index element={<HomePage />} />
            <Route path="dashboard" element={cashier ? <CashierDashboardPage /> : <DashboardPage />} />
            <Route path="ventas" element={<SalesModulePage />} />
            <Route path="pos" element={<PosPage />} />
            <Route path="returns" element={<ReturnsPage />} />
            <Route path="promotions" element={<PromotionsPage />} />
            <Route path="advances" element={<AdvancesPage />} />
            <Route path="collections" element={<CollectionsPage />} />
            <Route path="products" element={<ProductsPage />} />
            <Route path="inventario" element={<InventoryModulePage />} />
            <Route path="inventory" element={<InventoryPage />} />
            <Route path="inventory-quick" element={<InventoryQuickPage />} />
            <Route path="inventory-variants" element={<InventoryVariantsPage />} />
            <Route path="inventory-entries" element={<InventoryEntriesPage />} />
            <Route path="inventory-exits" element={<InventoryExitsPage />} />
            <Route path="inventory-serials" element={<InventorySerialsPage />} />
            <Route path="inventory-lots" element={<InventoryLotsPage />} />
            <Route path="inventory-transfers" element={<InventoryTransfersPage />} />
            <Route path="inventory-quality" element={<InventoryQualityPage />} />
            <Route path="inventory-labels" element={<InventoryLabelsPage />} />
            <Route path="inventory-physical" element={<InventoryPhysicalPage />} />
            <Route path="inventory-stockapp" element={<InventoryStockAppPage />} />
            <Route path="inventory-kardex" element={<InventoryKardexPage />} />
            <Route path="compras" element={<PurchasingModulePage />} />
            <Route path="purchases" element={<PurchasesPage />} />
            <Route path="purchase-orders" element={<PurchaseOrdersPage />} />
            <Route path="purchase-returns" element={<PurchaseReturnsPage />} />
            <Route path="suppliers" element={<SuppliersPage />} />
            <Route path="payables" element={<PayablesPage />} />
            <Route path="supplier-visits" element={<SupplierVisitsPage />} />
            <Route path="purchase-xml" element={<PurchaseXmlPage />} />
            <Route path="customers" element={<CustomersPage />} />
            <Route path="cashier" element={<CashierPage />} />
            <Route path="sales-by-cashier" element={<SalesByCashierPage />} />
            <Route path="branches" element={<BranchesPage />} />
            <Route path="users" element={<UsersPage />} />
            <Route path="roles" element={<RolesPage />} />
            <Route path="documents" element={<DocumentsPage />} />
            <Route path="payments" element={<PaymentsModulePage />} />
            <Route path="pagos" element={<PaymentsModulePage />} />
            <Route path="pay-recharge" element={<PaymentRechargePage />} />
            <Route path="pay-service" element={<PaymentServicePage />} />
            <Route path="pay-deposit" element={<PaymentDepositPage />} />
            <Route path="pay-deposits" element={<PaymentDepositsPage />} />
            <Route path="pay-operations" element={<PaymentOperationsPage />} />
            <Route path="pay-balance" element={<PaymentBalancePage />} />
            <Route path="facturacion" element={<InvoicingModulePage />} />
            <Route path="invoicing" element={<InvoicingPage />} />
            <Route path="invoicing-issuer" element={<InvoicingIssuerPage />} />
            <Route path="invoicing-list" element={<InvoicingListPage />} />
            <Route path="invoicing-receipt" element={<InvoicingReceiptPage />} />
            <Route path="invoicing-receipts" element={<InvoicingReceiptsPage />} />
            <Route path="invoicing-remissions" element={<InvoicingRemissionsPage />} />
            <Route path="invoicing-tickets" element={<InvoicingTicketsPage />} />
            <Route path="invoicing-closing" element={<InvoicingClosingPage />} />
            <Route path="invoicing-sat-products" element={<InvoicingSatProductsPage />} />
            <Route path="invoicing-sat-lines" element={<InvoicingSatLinesPage />} />
            <Route path="invoicing-carta-porte" element={<InvoicingCartaPortePage />} />
            <Route path="ticket-settings" element={<TicketSettingsPage />} />
            <Route path="ticket-reprint" element={<TicketReprintPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </>
        )}
      </Route>
    </Routes>
  );
}
