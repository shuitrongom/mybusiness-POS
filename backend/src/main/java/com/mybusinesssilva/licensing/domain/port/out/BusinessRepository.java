package com.mybusinesssilva.licensing.domain.port.out;

import com.mybusinesssilva.licensing.domain.model.Business;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de negocios (tenants) en el schema global {@code admin}.
 */
public interface BusinessRepository {

    /** Inserta un negocio nuevo y devuelve la entidad con su id asignado. */
    Business insert(Business business);

    /** Actualiza un negocio existente. */
    void update(Business business);

    /** Elimina el registro del negocio y sus módulos habilitados del schema {@code admin}. */
    void delete(long businessId);

    Optional<Business> findById(long id);

    Optional<Business> findBySchemaName(String schemaName);

    List<Business> findAll();

    /** Negocios en prueba cuya fecha de vencimiento ya pasó (para el proceso de expiración). */
    List<Business> findTrialsDueForExpiration();
}
