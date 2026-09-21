package com.mybusinesssilva;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.bi.application.BiService;
import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.invoicing.application.InvoicingService;
import com.mybusinesssilva.invoicing.domain.model.CfdiConcept;
import com.mybusinesssilva.invoicing.domain.model.CfdiStatus;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.application.PlanService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.model.Plan;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.security.roles.RoleService;
import com.mybusinesssilva.sales.application.SaleService;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pruebas de integración de las funcionalidades completadas al final:
 * devoluciones (reponen inventario), complemento de pagos, factura global, CRUD de planes y roles.
 */
class NewFeaturesIT extends AbstractIntegrationTest {

    @Autowired private LicensingService licensingService;
    @Autowired private PlanService planService;
    @Autowired private PlanRepository planRepository;
    @Autowired private CatalogService catalogService;
    @Autowired private InventoryService inventoryService;
    @Autowired private SaleService saleService;
    @Autowired private InvoicingService invoicingService;
    @Autowired private BiService biService;
    @Autowired private RoleService roleService;
    @Autowired private DataSource dataSource;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void superAdminCanCrudPlans() {
        Plan created = planService.create("admin@test", "STARTER-" + System.nanoTime(),
                "Plan de prueba", "desc", new BigDecimal("999.00"), List.of("sales", "inventory"));
        assertThat(created.id()).isNotNull();
        assertThat(created.moduleKeys()).contains("sales", "inventory");

        Plan updated = planService.update("admin@test", created.id(), "Plan editado", "d2",
                new BigDecimal("1200.00"), true, List.of("sales"));
        assertThat(updated.name()).isEqualTo("Plan editado");
        assertThat(updated.moduleKeys()).containsExactly("sales");
    }

    @Test
    void returnRestocksInventory() {
        long branchId = setupBusiness();
        long productId = createProduct("Refresco");
        inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                new BigDecimal("100"), "COMPRA", "t");
        // Vender 10 -> queda 90
        inventoryService.applyMovement(productId, branchId, MovementType.SALE,
                new BigDecimal("10"), "V", "t");
        assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("90");

        // Devolver 4 -> queda 94
        saleService.registerReturn(branchId,
                List.of(new SaleService.ReturnItem(productId, new BigDecimal("4"))), "cajero");
        assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("94");
    }

    @Test
    void paymentComplementOnStampedInvoice() {
        setupBusiness();
        ReceiverInfo receiver = new ReceiverInfo("XAXX010101000", "Cliente", "64000", "616", "G03");
        List<CfdiConcept> concepts = List.of(new CfdiConcept("50200000", "H87", "Producto",
                BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("100.00")));
        InvoicingService.InvoiceResult inv =
                invoicingService.issueInvoice(null, receiver, concepts, "pc-inv-1");
        assertThat(inv.status()).isEqualTo(CfdiStatus.STAMPED);

        long complementId = invoicingService.registerPaymentComplement(
                inv.cfdiId(), new BigDecimal("50.00"), "03", "2026-09-21");
        assertThat(complementId).isPositive();
    }

    @Test
    void globalInvoiceIsStamped() {
        setupBusiness();
        InvoicingService.InvoiceResult global =
                invoicingService.issueGlobalInvoice(new BigDecimal("1500.00"), "Ventas del día");
        assertThat(global.status()).isEqualTo(CfdiStatus.STAMPED);
        assertThat(global.uuid()).isNotBlank();
    }

    @Test
    void tenantHasPredefinedRoles() {
        setupBusiness();
        List<Map<String, Object>> roles = roleService.listRoles();
        assertThat(roles).extracting(r -> r.get("code"))
                .contains("OWNER", "ADMIN", "SUPERVISOR", "CASHIER");

        long roleId = roleService.createRole("VENDEDOR", "Vendedor");
        roleService.grantPermission(roleId, "sales", "CREATE");
        assertThat(roleService.permissionsOf(roleId)).anyMatch(
                p -> "sales".equals(p.get("moduleKey")) && "CREATE".equals(p.get("action")));
    }

    // --- helpers ---

    private long setupBusiness() {
        long planId = planRepository.findByCode("ENTERPRISE").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio Nuevas Feat", null, "abarrotes", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
        return new JdbcTemplate(dataSource).queryForObject(
                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, "Matriz");
    }

    private long createProduct(String name) {
        Product p = catalogService.createProduct(new Product(
                null, null, name, null, "pieza", false, null, null,
                new BigDecimal("10"), BigDecimal.ZERO, true, List.of(), Map.of()));
        return p.id();
    }
}
