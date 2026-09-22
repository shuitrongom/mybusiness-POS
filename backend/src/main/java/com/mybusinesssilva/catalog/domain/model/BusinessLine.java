package com.mybusinesssilva.catalog.domain.model;

/**
 * Perfil de giro (línea de negocio) administrado por el Super Admin.
 *
 * <p>Un giro nuevo es configuración, no código: define un {@code code} estable (usado por los
 * negocios) y datos descriptivos. Los campos dinámicos y módulos sugeridos por giro se
 * gestionan en tablas relacionadas.
 *
 * @param code        clave estable del giro (por ejemplo {@code abarrotes})
 * @param name        nombre visible (por ejemplo {@code Abarrotes})
 * @param description descripción del giro (puede ser nula)
 */
public record BusinessLine(String code, String name, String description) {
}
