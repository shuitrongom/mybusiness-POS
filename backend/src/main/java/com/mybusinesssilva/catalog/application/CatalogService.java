package com.mybusinesssilva.catalog.application;

import com.mybusinesssilva.catalog.domain.model.MasterProduct;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.catalog.domain.port.out.MasterProductRepository;
import com.mybusinesssilva.catalog.domain.port.out.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del catálogo de un negocio.
 *
 * <p>Incluye la lógica del "catálogo maestro que crece con el uso": al dar de alta un producto
 * con código de barras que no existe en el maestro, se aporta al maestro compartido para
 * beneficio de futuros negocios (efecto de red). El catálogo de cada negocio permanece aislado.
 */
@Service
public class CatalogService {

    private final ProductRepository productRepository;
    private final MasterProductRepository masterProductRepository;

    public CatalogService(ProductRepository productRepository,
                          MasterProductRepository masterProductRepository) {
        this.productRepository = productRepository;
        this.masterProductRepository = masterProductRepository;
    }

    /**
     * Da de alta un producto en el negocio y, si tiene código de barras, lo aporta al catálogo
     * maestro compartido.
     */
    @Transactional
    public Product createProduct(Product product) {
        Product saved = productRepository.insert(product);

        if (!product.barcodes().isEmpty()) {
            String barcode = product.barcodes().get(0);
            masterProductRepository.contribute(new MasterProduct(
                    null, barcode, product.name(), null, null,
                    product.unit(), product.satProdServ(), product.satUnit()));
        }
        return saved;
    }

    @Transactional
    public Product updateProduct(Product product) {
        productRepository.update(product);
        return productRepository.findById(product.id()).orElseThrow();
    }

    /**
     * Busca un producto del negocio por código de barras. Si no existe en el negocio, ofrece la
     * sugerencia del catálogo maestro compartido (si la hay) para agilizar el alta.
     */
    public BarcodeLookup lookupByBarcode(String barcode) {
        Optional<Product> local = productRepository.findByBarcode(barcode);
        if (local.isPresent()) {
            return new BarcodeLookup(local.get(), null);
        }
        Optional<MasterProduct> suggestion = masterProductRepository.findByBarcode(barcode);
        return new BarcodeLookup(null, suggestion.orElse(null));
    }

    public List<Product> search(String text) {
        return productRepository.search(text);
    }

    public List<Product> listAll() {
        return productRepository.findAll();
    }

    public List<MasterProduct> searchMaster(String text) {
        return masterProductRepository.search(text);
    }

    /**
     * Resultado de buscar por código de barras: o el producto del negocio, o una sugerencia
     * del catálogo maestro para dar de alta más rápido.
     *
     * @param product    producto del negocio si ya existe (nulo si no)
     * @param suggestion sugerencia del catálogo maestro si el negocio no lo tiene (nulo si no hay)
     */
    public record BarcodeLookup(Product product, MasterProduct suggestion) {
        public boolean foundInBusiness() {
            return product != null;
        }
    }
}
