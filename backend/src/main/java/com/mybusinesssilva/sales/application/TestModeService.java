package com.mybusinesssilva.sales.application;

import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.platform.util.DeviceDetector;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del MODO PRUEBA del negocio (ventas de práctica, Opción B).
 *
 * <p>
 * Lee/actualiza el flag {@code admin.business.test_mode} del tenant en curso y
 * audita cada cambio (quién, cuándo, desde qué dispositivo) en
 * {@code admin.audit_log_global}. Opción B: activar/desactivar el modo NUNCA
 * toca datos operativos (no borra ventas, no resetea inventario ni cortes); solo
 * cambia el flag que decide si las ventas nuevas nacen {@code is_test = true}.
 */
@Service
public class TestModeService {

    private final JdbcClient jdbc;
    private final AuditService auditService;

    public TestModeService(JdbcClient jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    /**
     * Estado actual del modo prueba del negocio en curso, con la bitácora reciente
     * de cambios (quién/cuándo/dispositivo).
     */
    public TestModeStatus getStatus() {
        BusinessRow business = loadBusiness();
        List<AuditEvent> recent = recentChanges(business.id());
        return new TestModeStatus(business.testMode(), business.id(), recent);
    }

    /**
     * Estado LIGERO (solo el flag) para que cualquier usuario del tenant —incluida
     * la cajera— pueda pintar el banner de "modo prueba" sin exponer la bitácora.
     */
    public boolean isActive() {
        return loadBusiness().testMode();
    }

    /**
     * Activa o desactiva el modo prueba del negocio en curso y audita el cambio.
     * Opción B: no toca datos operativos.
     *
     * @param active    nuevo estado del modo prueba
     * @param actor     quién lo cambia (subject del token)
     * @param userAgent encabezado {@code User-Agent} de la petición (para derivar
     *                  el dispositivo); puede ser {@code null}
     * @return el estado actualizado, con la bitácora refrescada
     */
    @Transactional
    public TestModeStatus setActive(boolean active, String actor, String userAgent) {
        BusinessRow business = loadBusiness();
        jdbc.sql("""
                UPDATE admin.business
                SET test_mode = :active
                WHERE schema_name = :schema
                """)
                .param("active", active)
                .param("schema", TenantContext.getTenantId())
                .update();

        String device = DeviceDetector.classify(userAgent);
        auditService.recordGlobal(
                actor,
                active ? "ENABLE_TEST_MODE" : "DISABLE_TEST_MODE",
                "business",
                String.valueOf(business.id()),
                Map.of(
                        "actor", actor == null ? "" : actor,
                        "device", device,
                        "userAgent", userAgent == null ? "" : userAgent,
                        "at", OffsetDateTime.now().toString()));

        return new TestModeStatus(active, business.id(), recentChanges(business.id()));
    }

    /**
     * Carga el negocio del tenant en curso ({@code id} + {@code test_mode}). Si no
     * hay tenant o no existe la fila, lanza error explícito (el endpoint es
     * OWNER-only sobre un tenant real).
     */
    private BusinessRow loadBusiness() {
        String schema = TenantContext.getTenantId();
        if (schema == null || schema.isBlank()) {
            throw new IllegalStateException("No hay negocio en contexto para administrar el modo prueba.");
        }
        return jdbc.sql("""
                SELECT id, COALESCE(test_mode, false) AS test_mode
                FROM admin.business
                WHERE schema_name = :schema
                """)
                .param("schema", schema)
                .query((rs, n) -> new BusinessRow(rs.getLong("id"), rs.getBoolean("test_mode")))
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el negocio del tenant en curso."));
    }

    /**
     * Últimos cambios de modo prueba del negocio (bitácora), leídos de
     * {@code admin.audit_log_global}. El dispositivo/UA viven en {@code details}
     * (JSONB); se extraen con el operador {@code ->>}.
     */
    private List<AuditEvent> recentChanges(long businessId) {
        return jdbc.sql("""
                SELECT actor, action,
                       COALESCE(details ->> 'device', 'unknown') AS device,
                       COALESCE(details ->> 'userAgent', '')      AS user_agent,
                       created_at
                FROM admin.audit_log_global
                WHERE action IN ('ENABLE_TEST_MODE', 'DISABLE_TEST_MODE')
                  AND target_type = 'business'
                  AND target_id = :businessId
                ORDER BY created_at DESC
                LIMIT 20
                """)
                .param("businessId", String.valueOf(businessId))
                .query((rs, n) -> new AuditEvent(
                        rs.getString("actor"),
                        rs.getString("action"),
                        rs.getString("device"),
                        rs.getString("user_agent"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    /** Fila ligera del negocio del tenant: id + flag de modo prueba. */
    private record BusinessRow(long id, boolean testMode) {
    }

    /**
     * Estado del modo prueba del negocio.
     *
     * @param active        {@code true} si el modo prueba está activo
     * @param businessId    id del negocio
     * @param recentChanges bitácora reciente de activaciones/desactivaciones
     */
    public record TestModeStatus(boolean active, long businessId, List<AuditEvent> recentChanges) {
    }

    /**
     * Evento de auditoría de un cambio de modo prueba.
     *
     * @param actor     quién lo hizo
     * @param action    {@code ENABLE_TEST_MODE} / {@code DISABLE_TEST_MODE}
     * @param device    {@code desktop} / {@code mobile} / {@code unknown}
     * @param userAgent User-Agent completo de la petición
     * @param at        cuándo ocurrió
     */
    public record AuditEvent(String actor, String action, String device, String userAgent,
            OffsetDateTime at) {
    }
}
