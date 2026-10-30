package com.mybusinesssilva.inventory.rawmaterial;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterialMovementType;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.out.RawMaterialRepository;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integración del repositorio JDBC de materia prima sobre un schema de prueba:
 * upsert atómico, {@code deductIfEnough} estricto, {@code balance_after} del kardex,
 * y la invariante enum↔CHECK de {@code RawMaterialMovementType}.
 */
class JdbcRawMaterialRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private RawMaterialRepository repository;
    @Autowired
    private DataSource dataSource;

    private long branchId;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private long setup() {
        long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@rm.com", "Panaderia RM", null, "panaderia", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
        branchId = new JdbcTemplate(dataSource).queryForObject(
                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, "Matriz");
        return repository.insert(new RawMaterial(
                null, null, "Harina", "kg", new BigDecimal("18"), new BigDecimal("5"), true));
    }

    @Test
    void adjustQuantityUpsertsAndDeductIsStrict() {
        long rm = setup();
        // No existe fila de stock: deductIfEnough devuelve false (no crea fila).
        assertThat(repository.deductIfEnough(rm, branchId, new BigDecimal("1"))).isFalse();
        // Primera entrada materializa la fila y devuelve el balance.
        assertThat(repository.adjustQuantity(rm, branchId, new BigDecimal("50"))).isEqualByComparingTo("50");
        // Segunda entrada suma.
        assertThat(repository.adjustQuantity(rm, branchId, new BigDecimal("10"))).isEqualByComparingTo("60");
        // Descuento estricto que alcanza.
        assertThat(repository.deductIfEnough(rm, branchId, new BigDecimal("40"))).isTrue();
        assertThat(repository.stock(rm, branchId)).isEqualByComparingTo("20");
        // Descuento que NO alcanza no deja negativo.
        assertThat(repository.deductIfEnough(rm, branchId, new BigDecimal("30"))).isFalse();
        assertThat(repository.stock(rm, branchId)).isEqualByComparingTo("20");
    }

    @Test
    void recordMovementStoresBalanceAndFrozenCost() {
        long rm = setup();
        BigDecimal balance = repository.adjustQuantity(rm, branchId, new BigDecimal("40"));
        repository.recordMovement(rm, branchId, RawMaterialMovementType.PURCHASE_RM,
                new BigDecimal("40"), balance, new BigDecimal("18.50"), "Entrada", null, "admin");
        BigDecimal stored = new JdbcTemplate(dataSource).queryForObject(
                "SELECT balance_after FROM raw_material_movement WHERE raw_material_id = ? ORDER BY id DESC LIMIT 1",
                BigDecimal.class, rm);
        assertThat(stored).isEqualByComparingTo("40");
        BigDecimal cost = new JdbcTemplate(dataSource).queryForObject(
                "SELECT unit_cost FROM raw_material_movement WHERE raw_material_id = ? ORDER BY id DESC LIMIT 1",
                BigDecimal.class, rm);
        assertThat(cost).isEqualByComparingTo("18.50");
    }

    @Test
    void hasMovementsAndHasAnyStockReflectState() {
        long rm = setup();
        // Insumo recién creado: sin existencia ni movimientos (virgen).
        assertThat(repository.hasAnyStock(rm)).isFalse();
        assertThat(repository.hasMovements(rm)).isFalse();
        // Tras surtir, hay existencia y un movimiento.
        BigDecimal balance = repository.adjustQuantity(rm, branchId, new BigDecimal("30"));
        repository.recordMovement(rm, branchId, RawMaterialMovementType.PURCHASE_RM,
                new BigDecimal("30"), balance, new BigDecimal("18"), "Entrada", null, "admin");
        assertThat(repository.hasAnyStock(rm)).isTrue();
        assertThat(repository.hasMovements(rm)).isTrue();
        // Al consumir todo, el stock queda en 0 (hasAnyStock false) pero el historial permanece.
        assertThat(repository.deductIfEnough(rm, branchId, new BigDecimal("30"))).isTrue();
        assertThat(repository.hasAnyStock(rm)).isFalse();
        assertThat(repository.hasMovements(rm)).isTrue();
    }

    @Test
    void hardDeleteRemovesVirginAndLeavesUsedUntouched() {
        long virgin = setup();
        // Un segundo insumo CON historial, que NO debe tocarse.
        long used = repository.insert(new RawMaterial(
                null, null, "Azucar", "kg", new BigDecimal("22"), new BigDecimal("3"), true));
        BigDecimal balance = repository.adjustQuantity(used, branchId, new BigDecimal("15"));
        repository.recordMovement(used, branchId, RawMaterialMovementType.PURCHASE_RM,
                new BigDecimal("15"), balance, new BigDecimal("22"), "Entrada", null, "admin");

        repository.hardDelete(virgin);

        assertThat(repository.find(virgin)).isEmpty();
        // El insumo con historial sigue intacto (catálogo, existencia y kardex).
        assertThat(repository.find(used)).isPresent();
        assertThat(repository.hasAnyStock(used)).isTrue();
        assertThat(repository.hasMovements(used)).isTrue();
    }

    @Test
    void everyEnumLiteralIsAcceptedByCheckConstraint() {
        long rm = setup();
        BigDecimal balance = repository.adjustQuantity(rm, branchId, new BigDecimal("100"));
        // Cada literal del enum DEBE ser aceptado por chk_rm_movement_type.
        for (RawMaterialMovementType type : RawMaterialMovementType.values()) {
            repository.recordMovement(rm, branchId, type, new BigDecimal("1"), balance,
                    BigDecimal.ZERO, "check", null, "admin");
        }
        Long count = new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM raw_material_movement WHERE raw_material_id = ?", Long.class, rm);
        assertThat(count).isEqualTo((long) RawMaterialMovementType.values().length);
    }
}
