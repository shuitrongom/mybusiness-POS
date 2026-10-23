package com.mybusinesssilva.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteSummary;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.inventory.domain.model.StockLevel;
import com.mybusinesssilva.inventory.domain.port.out.InventoryRepository;
import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.sales.application.TestModeService.CutoverSummary;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Pruebas de servicio del corte PRUEBA→REAL ({@link TestModeService#cutover}).
 *
 * <p>
 * {@link InventoryService}, {@link InventoryRepository}, {@link BakeryUseCase} y
 * {@link AuditService} se mockean directamente. El chain fluido de
 * {@code JdbcClient} se arma con un stub que, cuando el servicio llama
 * {@code query(RowMapper)}, EJECUTA el mapper real contra un {@link ResultSet}
 * simulado (así se obtiene el {@code BusinessRow} privado sin acoplarse a él) y
 * cuando llama {@code query(Class)} devuelve los COUNT de práctica.
 */
class TestModeServiceTest {

    private static final String SCHEMA = "tenant_9";
    private static final String REASON =
            "Cierre de modo prueba: ajuste a 0 para iniciar operación real";

    private JdbcClient jdbc;
    private AuditService auditService;
    private InventoryService inventoryService;
    private InventoryRepository inventoryRepository;
    private BakeryUseCase bakeryUseCase;
    private TestModeService service;

    private JdbcClient.StatementSpec stmt;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcClient.class);
        auditService = mock(AuditService.class);
        inventoryService = mock(InventoryService.class);
        inventoryRepository = mock(InventoryRepository.class);
        bakeryUseCase = mock(BakeryUseCase.class);

        stmt = mock(JdbcClient.StatementSpec.class);
        when(jdbc.sql(any(String.class))).thenReturn(stmt);
        lenient().when(stmt.param(any(String.class), any())).thenReturn(stmt);
        lenient().when(stmt.update()).thenReturn(1);

        service = new TestModeService(jdbc, auditService, inventoryService,
                inventoryRepository, bakeryUseCase);
        TenantContext.setTenantId(SCHEMA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * Arma el chain de {@code JdbcClient} para una corrida del corte:
     * <ul>
     * <li>{@code query(RowMapper)} -> ejecuta el mapper contra un ResultSet que
     * devuelve {@code id=businessId} y {@code test_mode=testMode}. Lo usan tanto
     * {@code loadBusinessForUpdate()} (via {@code optional()}) como
     * {@code recentChanges()} (via {@code list()}, que devolvemos vacío).</li>
     * <li>{@code query(Class)} -> COUNT de práctica (sales, luego cashCuts) via
     * {@code single()}.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private void stubJdbc(long businessId, boolean testMode, long sales, long cashCuts) {
        // query(RowMapper): el servicio lo usa para BusinessRow y para AuditEvent.
        when(stmt.query(any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<Object> mapper = inv.getArgument(0);
            JdbcClient.MappedQuerySpec<Object> spec = mock(JdbcClient.MappedQuerySpec.class);
            // loadBusinessForUpdate(): optional() -> BusinessRow mapeado.
            lenient().when(spec.optional()).thenAnswer(o -> {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("id")).thenReturn(businessId);
                when(rs.getBoolean("test_mode")).thenReturn(testMode);
                return Optional.of(mapper.mapRow(rs, 1));
            });
            // recentChanges(): list() -> vacío (no nos interesa el contenido aquí).
            lenient().when(spec.list()).thenReturn(List.of());
            return spec;
        });
        // query(Class) para los COUNT (Long): primero sales, luego cashCuts.
        JdbcClient.MappedQuerySpec<Object> counts = mock(JdbcClient.MappedQuerySpec.class);
        lenient().when(counts.single()).thenReturn(sales, cashCuts);
        when(stmt.query(any(Class.class))).thenReturn(counts);
    }

    @Test
    void adjustsEachStockRowToZeroWithExactReasonAndActor() {
        stubJdbc(9L, true, 128L, 6L);
        when(bakeryUseCase.wasteSummary()).thenReturn(new WasteSummary(new BigDecimal("90"), 3, new BigDecimal("450")));
        when(inventoryRepository.stockWithBalance()).thenReturn(List.of(
                new StockLevel(101L, 1L, new BigDecimal("48"), BigDecimal.ZERO),
                new StockLevel(102L, 1L, new BigDecimal("30"), BigDecimal.ZERO)));

        CutoverSummary summary = service.cutover("owner@panaderia.mx", "UA-desktop");

        verify(inventoryService).adjust(101L, 1L, new BigDecimal("-48"), REASON, "owner@panaderia.mx");
        verify(inventoryService).adjust(102L, 1L, new BigDecimal("-30"), REASON, "owner@panaderia.mx");
        verify(inventoryService, times(2)).adjust(anyLong(), anyLong(), any(), any(), any());

        assertThat(summary.executed()).isTrue();
        assertThat(summary.active()).isFalse();
        assertThat(summary.businessId()).isEqualTo(9L);
        assertThat(summary.stock().rowsAdjusted()).isEqualTo(2);
        assertThat(summary.stock().totalUnitsRemoved()).isEqualByComparingTo("78");
        assertThat(summary.practice().salesKept()).isEqualTo(128L);
        assertThat(summary.practice().cashCutsKept()).isEqualTo(6L);
        assertThat(summary.waste().totalPieces()).isEqualByComparingTo("90");
        assertThat(summary.waste().productCount()).isEqualTo(3);
    }

    @Test
    void idempotentWhenTestModeAlreadyOff() {
        stubJdbc(9L, false, 128L, 6L);
        when(bakeryUseCase.wasteSummary()).thenReturn(new WasteSummary(new BigDecimal("90"), 3, new BigDecimal("450")));

        CutoverSummary summary = service.cutover("owner@panaderia.mx", "UA");

        assertThat(summary.executed()).isFalse();
        assertThat(summary.active()).isFalse();
        assertThat(summary.stock().rowsAdjusted()).isZero();
        assertThat(summary.stock().lines()).isEmpty();
        verify(inventoryService, never()).adjust(anyLong(), anyLong(), any(), any(), any());
        verify(inventoryRepository, never()).stockWithBalance();
        verifyNoInteractions(auditService);
    }

    @Test
    void propagatesWhenAdjustFailsAndDoesNotAudit() {
        stubJdbc(9L, true, 1L, 1L);
        when(bakeryUseCase.wasteSummary()).thenReturn(new WasteSummary(BigDecimal.ZERO, 0, BigDecimal.ZERO));
        when(inventoryRepository.stockWithBalance()).thenReturn(List.of(
                new StockLevel(101L, 1L, new BigDecimal("48"), BigDecimal.ZERO)));
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(inventoryService).adjust(anyLong(), anyLong(), any(), any(), any());

        assertThatThrownBy(() -> service.cutover("owner@panaderia.mx", "UA"))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void onlyReadsWasteNeverWrites() {
        stubJdbc(9L, true, 0L, 0L);
        when(bakeryUseCase.wasteSummary()).thenReturn(new WasteSummary(new BigDecimal("90"), 3, new BigDecimal("450")));
        when(inventoryRepository.stockWithBalance()).thenReturn(List.of());

        service.cutover("owner@panaderia.mx", "UA");

        verify(bakeryUseCase).wasteSummary();
        verify(bakeryUseCase, never()).transferToWaste(anyLong(), anyLong(), any(), any(), any(), any(), any());
        verify(bakeryUseCase, never()).returnWasteOnVoid(anyLong(), anyLong(), any(), any(), anyLong());
        verify(bakeryUseCase, never()).sellWaste(anyLong(), anyLong(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void auditsCutoverWithDeviceUserAgentAndSummary() {
        stubJdbc(9L, true, 128L, 6L);
        when(bakeryUseCase.wasteSummary()).thenReturn(new WasteSummary(new BigDecimal("90"), 3, new BigDecimal("450")));
        when(inventoryRepository.stockWithBalance()).thenReturn(List.of(
                new StockLevel(101L, 1L, new BigDecimal("48"), BigDecimal.ZERO)));

        service.cutover("owner@panaderia.mx",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)");

        ArgumentCaptor<Map<String, ?>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordGlobal(eq("owner@panaderia.mx"), eq("TEST_MODE_CUTOVER"),
                eq("business"), eq("9"), details.capture());

        Map<String, ?> d = details.getValue();
        assertThat(d.get("device")).isEqualTo("mobile");
        assertThat(d.get("salesKept")).isEqualTo(128L);
        assertThat(d.get("cashCutsKept")).isEqualTo(6L);
        assertThat(d.get("stockRowsAdjusted")).isEqualTo(1);
        assertThat(d.get("wasteProducts")).isEqualTo(3);
        assertThat(d).containsKeys("actor", "userAgent", "at", "stockUnitsRemoved", "wastePieces");
    }
}
