package com.mybusinesssilva.catalog.application;

import com.mybusinesssilva.catalog.domain.model.BusinessLine;
import com.mybusinesssilva.catalog.domain.port.out.BusinessLineRepository;
import com.mybusinesssilva.platform.audit.AuditService;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de gestión de giros (líneas de negocio) para el Super Admin.
 *
 * <p>Un giro es configuración: se puede crear, editar y eliminar sin cambios de código. El
 * código del giro se normaliza a minúsculas con guiones bajos para que sea estable y comparable.
 */
@Service
public class BusinessLineService {

    private static final Pattern CODE_ALLOWED = Pattern.compile("[^a-z0-9_]");

    private final BusinessLineRepository repository;
    private final AuditService auditService;

    public BusinessLineService(BusinessLineRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<BusinessLine> list() {
        return repository.findAll();
    }

    /**
     * Crea un giro nuevo. El código se deriva del nombre si no se envía uno explícito.
     *
     * @param code        código propuesto (opcional; se normaliza)
     * @param name        nombre visible (obligatorio)
     * @param description descripción (opcional)
     * @return el giro creado
     */
    @Transactional
    public BusinessLine create(String actor, String code, String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del giro es obligatorio");
        }
        String normalized = normalizeCode((code == null || code.isBlank()) ? name : code);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("El código del giro no puede quedar vacío");
        }
        if (repository.findByCode(normalized).isPresent()) {
            throw new IllegalArgumentException("Ya existe un giro con el código: " + normalized);
        }
        BusinessLine line = new BusinessLine(normalized, name.trim(), trimOrNull(description));
        repository.insert(line);
        auditService.recordGlobal(actor, "BUSINESS_LINE_CREATED", "business_line",
                normalized, Map.of("name", name));
        return line;
    }

    /** Actualiza el nombre y la descripción de un giro existente (el código no cambia). */
    @Transactional
    public BusinessLine update(String actor, String code, String name, String description) {
        String normalized = normalizeCode(code);
        repository.findByCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Giro inexistente: " + normalized));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del giro es obligatorio");
        }
        BusinessLine line = new BusinessLine(normalized, name.trim(), trimOrNull(description));
        repository.update(line);
        auditService.recordGlobal(actor, "BUSINESS_LINE_UPDATED", "business_line",
                normalized, Map.of("name", name));
        return line;
    }

    /**
     * Elimina un giro. No permite borrar si hay negocios que lo usan (evita dejarlos huérfanos).
     */
    @Transactional
    public void delete(String actor, String code) {
        String normalized = normalizeCode(code);
        long inUse = repository.countBusinessesUsing(normalized);
        if (inUse > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar el giro: lo usan " + inUse + " negocio(s)");
        }
        repository.deleteByCode(normalized);
        auditService.recordGlobal(actor, "BUSINESS_LINE_DELETED", "business_line",
                normalized, Map.of());
    }

    private static String normalizeCode(String raw) {
        String lower = raw.trim().toLowerCase()
                .replace('á', 'a').replace('é', 'e').replace('í', 'i')
                .replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')
                .replace(' ', '_');
        return CODE_ALLOWED.matcher(lower).replaceAll("");
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
