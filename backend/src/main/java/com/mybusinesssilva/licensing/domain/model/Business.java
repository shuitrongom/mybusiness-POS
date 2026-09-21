package com.mybusinesssilva.licensing.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Entidad de dominio que representa a un negocio (tenant) y su ciclo de licencia.
 *
 * <p>Concentra las reglas del negocio de licenciamiento: cómo inicia una prueba, cuándo vence,
 * cómo se compra la licencia definitiva y cómo se suspende o reactiva. No depende de frameworks
 * ni de persistencia.
 */
public class Business {

    private Long id;
    private final String name;
    private final String rfc;
    private final String businessLine;
    private String schemaName;
    private String subdomain;
    private BusinessStatus status;
    private ConnectionKind connectionKind;
    private int trialMonths;
    private Instant trialStartsAt;
    private Instant trialEndsAt;
    private Instant purchasedAt;
    private Long planId;

    private Business(String name, String rfc, String businessLine, String businessLineNormalized) {
        this.name = name;
        this.rfc = rfc;
        this.businessLine = businessLine;
        this.connectionKind = ConnectionKind.DEFAULT;
    }

    /**
     * Crea un negocio nuevo en periodo de prueba.
     *
     * @param name        nombre del negocio
     * @param rfc         RFC (puede ser nulo si aún no lo tiene)
     * @param businessLine giro (abarrotes, panaderia, polleria, etc.)
     * @param planId      plan seleccionado
     * @param trialMonths meses de prueba (0 = sin prueba, arranca para compra inmediata)
     * @param clock       reloj (inyectable para pruebas deterministas)
     */
    public static Business createInTrial(String name, String rfc, String businessLine,
                                         Long planId, int trialMonths, Clock clock) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del negocio es obligatorio");
        }
        if (trialMonths < 0) {
            throw new IllegalArgumentException("Los meses de prueba no pueden ser negativos");
        }
        Business business = new Business(name, rfc, businessLine, null);
        business.planId = planId;
        business.trialMonths = trialMonths;
        business.status = trialMonths == 0 ? BusinessStatus.EXPIRED : BusinessStatus.TRIAL;
        Instant now = Instant.now(clock);
        business.trialStartsAt = now;
        business.trialEndsAt = trialMonths == 0 ? now : now.plus(trialMonths * 30L, ChronoUnit.DAYS);
        return business;
    }

    /**
     * Registra la compra de la licencia definitiva: acceso permanente, sin vencimiento.
     */
    public void purchaseLicense(Clock clock) {
        if (this.status == BusinessStatus.ACTIVE) {
            return; // Idempotente: ya tiene licencia.
        }
        this.status = BusinessStatus.ACTIVE;
        this.purchasedAt = Instant.now(clock);
    }

    /**
     * Evalúa el vencimiento de la prueba. Si está en prueba y ya pasó la fecha, la marca vencida.
     *
     * @return true si el estado cambió a EXPIRED en esta evaluación
     */
    public boolean expireTrialIfDue(Clock clock) {
        if (this.status == BusinessStatus.TRIAL
                && this.trialEndsAt != null
                && !Instant.now(clock).isBefore(this.trialEndsAt)) {
            this.status = BusinessStatus.EXPIRED;
            return true;
        }
        return false;
    }

    /** Suspende el negocio (sin acceso, conservando datos). */
    public void suspend() {
        this.status = BusinessStatus.SUSPENDED;
    }

    /** Reactiva un negocio suspendido, devolviéndolo a activo (licencia) . */
    public void reactivate() {
        if (this.status == BusinessStatus.SUSPENDED) {
            this.status = BusinessStatus.ACTIVE;
        }
    }

    /** @return true si el negocio permite acceso operativo en su estado actual. */
    public boolean allowsAccess() {
        return status.allowsAccess();
    }

    public void assignSchema(String schemaName) {
        this.schemaName = schemaName;
    }

    // --- Getters y asignaciones controladas para la capa de persistencia ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getRfc() {
        return rfc;
    }

    public String getBusinessLine() {
        return businessLine;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public BusinessStatus getStatus() {
        return status;
    }

    public void setStatus(BusinessStatus status) {
        this.status = status;
    }

    public ConnectionKind getConnectionKind() {
        return connectionKind;
    }

    public void setConnectionKind(ConnectionKind connectionKind) {
        this.connectionKind = connectionKind;
    }

    public int getTrialMonths() {
        return trialMonths;
    }

    public Instant getTrialStartsAt() {
        return trialStartsAt;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    /**
     * Rehidrata una entidad desde la persistencia (sin aplicar reglas de creación).
     */
    public static Business rehydrate(Long id, String name, String rfc, String businessLine,
                                     String schemaName, String subdomain, BusinessStatus status,
                                     ConnectionKind connectionKind, int trialMonths,
                                     Instant trialStartsAt, Instant trialEndsAt,
                                     Instant purchasedAt, Long planId) {
        Business b = new Business(name, rfc, businessLine, null);
        b.id = id;
        b.schemaName = schemaName;
        b.subdomain = subdomain;
        b.status = status;
        b.connectionKind = connectionKind;
        b.trialMonths = trialMonths;
        b.trialStartsAt = trialStartsAt;
        b.trialEndsAt = trialEndsAt;
        b.purchasedAt = purchasedAt;
        b.planId = planId;
        return b;
    }
}
