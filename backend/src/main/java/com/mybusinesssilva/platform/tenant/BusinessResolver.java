package com.mybusinesssilva.platform.tenant;

import java.util.Optional;

/**
 * Puerto compartido para resolver el {@code business_id} de {@code admin.business} a partir del
 * nombre del schema del tenant (resolución H-A1 del diseño). Evita duplicar el mismo SQL en los
 * bounded contexts {@code support} y {@code notifications}.
 */
public interface BusinessResolver {

    /**
     * Resuelve el identificador del negocio dueño del schema dado.
     *
     * @param tenantSchema nombre del schema del tenant (por ejemplo {@code tenant_9})
     * @return el {@code business_id} si existe, vacío si no se resuelve
     */
    Optional<Long> resolveBusinessIdBySchema(String tenantSchema);
}
