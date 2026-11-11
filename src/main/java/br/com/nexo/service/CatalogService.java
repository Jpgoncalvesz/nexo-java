package br.com.nexo.service;

import br.com.nexo.api.ApiException;
import br.com.nexo.api.Dtos;
import br.com.nexo.api.Dtos.*;
import br.com.nexo.domain.Product;
import br.com.nexo.repository.ProductRepository;
import java.math.RoundingMode;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final ProductRepository products;
    public CatalogService(ProductRepository products) { this.products = products; }

    @Transactional(readOnly = true)
    public PageView<ProductView> list(String q, int page, int size) {
        validatePage(page, size);
        return PageView.of(products.search(query(q), PageRequest.of(page, size, Sort.by("id"))).map(ProductView::of));
    }
    @Transactional
    public ProductView create(ProductCreate input) {
        String sku = Dtos.text(input.sku(), 40, "SKU").toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9_-]+")) throw ApiException.invalid("SKU deve conter apenas letras, números, hífen e sublinhado.");
        String name = Dtos.text(input.nome(), 120, "Nome");
        if (input.preco() == null || input.preco().signum() <= 0 || input.preco().compareTo(new java.math.BigDecimal("9999999999.99")) > 0 || input.preco().stripTrailingZeros().scale() > 2) throw ApiException.invalid("Preço deve ser positivo e ter até duas casas decimais.");
        int stock = Dtos.integer(input.estoque(), 0, 1_000_000, "Estoque");
        int minimum = Dtos.integer(input.estoqueMinimo(), 0, 1_000_000, "Estoque mínimo");
        if (products.existsBySku(sku)) throw ApiException.conflict("SKU_DUPLICADO", "Já existe um produto com esse SKU.");
        return ProductView.of(products.saveAndFlush(new Product(sku, name, input.preco().setScale(2, RoundingMode.UNNECESSARY), stock, minimum)));
    }
    @Transactional
    public ProductView replenish(long id, StockAdd input) {
        int amount = Dtos.integer(input.quantidade(), 1, 1_000_000, "Quantidade");
        Product product = products.findLocked(id).orElseThrow(() -> ApiException.missing("Produto não encontrado."));
        if (product.getStock() > 1_000_000 - amount) throw ApiException.conflict("LIMITE_ESTOQUE", "O saldo máximo permitido é de 1.000.000 unidades.");
        product.changeStock(amount);
        return ProductView.of(product);
    }
    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.invalid("Página deve ser não negativa e tamanho deve estar entre 1 e 100.");
    }
    static String query(String value) {
        if (value == null) return "";
        if (value.length() > 120) throw ApiException.invalid("Busca deve ter até 120 caracteres.");
        return value.trim();
    }
}
