package com.mybusinesssilva.inventory.bakery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.inventory.bakery.application.BakeryBatchService;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryBatch;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchInput;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchOutput;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.out.RawMaterialRepository;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
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
 * Integración de la TANDA de extremo a extremo sobre un schema de prueba:
 * cuadre atómico (MP baja + pan sube + tanda liga), rollback total ante fallo en la
 * pata pan DESPUÉS de consumir MP (atomicidad cross-bean), invariante
 * {@code production_id NOT NULL} tras el registro, y seeding de permisos del PANADERO
 * (CA-11).
 */
class BakeryBatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private BakeryBatchService batchService;
    @Autowired
    private RawMaterialRepository rawMaterialRepository;
    @Autowired
    private DataSource dataSource;

    private long branchId;
    private long harinaId;
    private long azucarId;
    private long conchaId;
    private long bolilloId;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private void setup() {
        long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@batch.com", "Panaderia Batch", null, "panaderia", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
        branchId = new JdbcTemplate(dataSource).queryForObject(
                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, "Matriz");
        conchaId = product("Concha", "15");
        bolilloId = product("Bolillo", "10");
        harinaId = rawMaterialRepository.insert(new RawMaterial(
                null, null, "Harina", "kg", new BigDecimal("18"), BigDecimal.ZERO, true));
        azucarId = rawMaterialRepository.insert(new RawMaterial(
                null, null, "Azúcar", "kg", new BigDecimal("22"), BigDecimal.ZERO, true));
        // Existencia inicial de insumos.
        rawMaterialRepository.adjustQuantity(harinaId, branchId, new BigDecimal("40"));
        rawMaterialRepository.adjustQuantity(azucarId, branchId, new BigDecimal("10"));
    }

    private long product(String name, String price) {
        Product p = catalogService.createProduct(new Product(
                null, null, name, null, "pieza", false, null, null,
                new BigDecimal(price), BigDecimal.ZERO, true, List.of(), Map.of()));
        return p.id();
    }

    private BakeryBatch batch(List<BatchOutput> outputs) {
        return new BakeryBatch(null, null, branchId, null, null,
                List.of(new BatchInput(harinaId, new BigDecimal("10"), null, null, null),
                        new BatchInput(azucarId, new BigDecimal("3"), null, null, null)),
                outputs, "Hornada", "panadero@x.com", "PANADERO");
    }

    @Test
    void batchBalancesBothAccountsAndLinksThem() {
        setup();
        long batchId = batchService.registerBatch(batch(List.of(
                new BatchOutput(conchaId, new BigDecimal("10"), null, null, null),
                new BatchOutput(bolilloId, new BigDecimal("30"), null, null, null))));

        JdbcTemplate db = new JdbcTemplate(dataSource);
        // MP baja: harina 40→30, azúcar 10→7.
        assertThat(rawMaterialRepository.stock(harinaId, branchId)).isEqualByComparingTo("30");
        assertThat(rawMaterialRepository.stock(azucarId, branchId)).isEqualByComparingTo("7");
        // Kardex de MP: dos CONSUME negativos con reference BATCH:<id>.
        Long consumeCount = db.queryForObject(
                "SELECT count(*) FROM raw_material_movement WHERE reference = ? AND movement_type = 'CONSUME'",
                Long.class, "BATCH:" + batchId);
        assertThat(consumeCount).isEqualTo(2L);

        // Pan sube: inventory_stock de concha y bolillo.
        assertThat(db.queryForObject(
                "SELECT quantity FROM inventory_stock WHERE product_id = ? AND branch_id = ?",
                BigDecimal.class, conchaId, branchId)).isEqualByComparingTo("10");
        assertThat(db.queryForObject(
                "SELECT quantity FROM inventory_stock WHERE product_id = ? AND branch_id = ?",
                BigDecimal.class, bolilloId, branchId)).isEqualByComparingTo("30");

        // Tanda ligada: production_id NOT NULL e invariante del valor.
        Map<String, Object> head = db.queryForMap(
                "SELECT folio, production_id, input_cost, output_value, value_over_inputs FROM bakery_batch WHERE id = ?",
                batchId);
        assertThat(head.get("folio")).isEqualTo("T-" + batchId);
        assertThat(head.get("production_id")).isNotNull();
        // input_cost = 10·18 + 3·22 = 246; output_value = 10·15 + 30·10 = 450; VOI = 204.
        assertThat((BigDecimal) head.get("input_cost")).isEqualByComparingTo("246");
        assertThat((BigDecimal) head.get("output_value")).isEqualByComparingTo("450");
        assertThat((BigDecimal) head.get("value_over_inputs")).isEqualByComparingTo("204");

        // 2 líneas de insumo y 2 de producción.
        assertThat(db.queryForObject("SELECT count(*) FROM bakery_batch_input WHERE batch_id = ?",
                Long.class, batchId)).isEqualTo(2L);
        assertThat(db.queryForObject("SELECT count(*) FROM bakery_batch_output WHERE batch_id = ?",
                Long.class, batchId)).isEqualTo(2L);

        // Cuadre: output_value de la tanda = Σ (qty·entry_price) de la producción ligada.
        long productionId = ((Number) head.get("production_id")).longValue();
        BigDecimal prodValue = db.queryForObject(
                "SELECT COALESCE(SUM(quantity*entry_price),0) FROM bakery_production_line WHERE production_id = ?",
                BigDecimal.class, productionId);
        assertThat(prodValue).isEqualByComparingTo((BigDecimal) head.get("output_value"));
    }

    @Test
    void rollsBackEverythingWhenPanLegFails() {
        setup();
        BigDecimal harinaBefore = rawMaterialRepository.stock(harinaId, branchId);

        // Output con productId inexistente: la pata pan (registerProduction → FK de
        // inventory_stock/product) falla DESPUÉS de consumir MP. Debe revertir todo.
        assertThatThrownBy(() -> batchService.registerBatch(batch(List.of(
                new BatchOutput(999_999L, new BigDecimal("5"), null, null, null)))))
                .isInstanceOf(Exception.class);

        JdbcTemplate db = new JdbcTemplate(dataSource);
        // MP intacta: ni se descontó ni quedó kardex CONSUME ni fila de tanda.
        assertThat(rawMaterialRepository.stock(harinaId, branchId)).isEqualByComparingTo(harinaBefore);
        assertThat(db.queryForObject(
                "SELECT count(*) FROM raw_material_movement WHERE movement_type = 'CONSUME'", Long.class))
                .isEqualTo(0L);
        assertThat(db.queryForObject("SELECT count(*) FROM bakery_batch", Long.class)).isEqualTo(0L);
    }

    @Test
    void everyBatchHasProductionIdNotNull() {
        setup();
        batchService.registerBatch(batch(List.of(
                new BatchOutput(conchaId, new BigDecimal("8"), null, null, null))));
        Long nullProd = new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM bakery_batch WHERE production_id IS NULL", Long.class);
        assertThat(nullProd).isEqualTo(0L);
    }

    @Test
    void panaderoRoleSeededWithPermissions() {
        setup();
        JdbcTemplate db = new JdbcTemplate(dataSource);
        // V47 sembró el rol PANADERO con permisos no vacíos sobre bakery_prod.
        Long perms = db.queryForObject("""
                SELECT count(*) FROM role_permission rp
                JOIN role r ON r.id = rp.role_id
                WHERE r.code = 'PANADERO' AND rp.module_key = 'bakery_prod'
                """, Long.class);
        assertThat(perms).isEqualTo(2L);
    }
}
