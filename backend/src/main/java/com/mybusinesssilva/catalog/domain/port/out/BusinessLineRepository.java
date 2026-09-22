package com.mybusinesssilva.catalog.domain.port.out;

import com.mybusinesssilva.catalog.domain.model.BusinessLine;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de giros (líneas de negocio) en el schema {@code admin}.
 */
public interface BusinessLineRepository {

    /** Devuelve todos los giros, ordenados por nombre. */
    List<BusinessLine> findAll();

    Optional<BusinessLine> findByCode(String code);

    /** Inserta un giro nuevo. */
    void insert(BusinessLine line);

    /** Actualiza el nombre y descripción de un giro existente. */
    void update(BusinessLine line);

    /** Elimina un giro por su código. */
    void deleteByCode(String code);

    /** @return número de negocios que usan este giro (para impedir borrados que dejen huérfanos). */
    long countBusinessesUsing(String code);
}
