package com.mybusinesssilva.catalog.domain.port.out;

import com.mybusinesssilva.catalog.domain.model.MasterProduct;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para el catálogo maestro compartido (schema {@code admin}).
 */
public interface MasterProductRepository {

    Optional<MasterProduct> findByBarcode(String barcode);

    List<MasterProduct> search(String text);

    /**
     * Registra un producto aportado por un negocio (origen USER) si no existe ya por su código.
     * Si ya existe, incrementa el contador de veces visto.
     *
     * @return el producto maestro resultante
     */
    MasterProduct contribute(MasterProduct product);
}
