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

    /**
     * Elimina un producto. Si el producto ya participó en ventas (existe en {@code sale_line}),
     * no puede borrarse físicamente sin romper el histórico; en ese caso se desactiva (borrado
     * lógico). Si no tiene ventas, se elimina físicamente junto con sus códigos de barras,
     * existencias y movimientos de inventario.
     *
     * @param id id del producto
     * @return true si se borró físicamente; false si solo se desactivó por tener ventas
     */
    boolean delete(long id);

    Optional<Product> findById(long id);

    Optional<Product> findByBarcode(String barcode);

    List<Product> search(String text);

    List<Product> findAll();
}
