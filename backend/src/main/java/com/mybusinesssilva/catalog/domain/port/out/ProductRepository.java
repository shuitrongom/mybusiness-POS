package com.mybusinesssilva.catalog.domain.port.out;

import com.mybusinesssilva.catalog.domain.model.Product;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de productos del negocio (en el schema del tenant).
 */
public interface ProductRepository {

    Product insert(Product product);

    void update(Product product);

    Optional<Product> findById(long id);

    Optional<Product> findByBarcode(String barcode);

    List<Product> search(String text);

    List<Product> findAll();
}
