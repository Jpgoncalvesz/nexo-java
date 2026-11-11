package br.com.nexo.api;

import br.com.nexo.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class Dtos {
    private Dtos() {}
    public record ProductCreate(String sku, String nome, BigDecimal preco, Integer estoque, Integer estoqueMinimo) {}
    public record StockAdd(Integer quantidade) {}
    public record ItemCreate(Long produtoId, Integer quantidade) {}
    public record OrderCreate(String cliente, List<ItemCreate> itens) {}
    public record ProductView(Long id, String sku, String nome, BigDecimal preco, int estoque, int estoqueMinimo) {
        public static ProductView of(Product p) { return new ProductView(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getStock(), p.getMinimumStock()); }
    }
    public record ItemView(Long produtoId, String nome, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {
        static ItemView of(OrderItem item) { return new ItemView(item.getProduct().getId(), item.getProduct().getName(), item.getQuantity(), item.getUnitPrice(), item.getSubtotal()); }
    }
    public record OrderView(Long id, String cliente, OrderStatus status, Instant criadoEm, String criadoPor, List<ItemView> itens, BigDecimal total) {
        public static OrderView of(PurchaseOrder order) {
            return new OrderView(order.getId(), order.getCustomer(), order.getStatus(), order.getCreatedAt(), order.getCreatedBy(),
                    order.getItems().stream().map(ItemView::of).toList(),
                    order.getItems().stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }
    public record PageView<T>(List<T> itens, int pagina, int tamanho, long totalElementos, int totalPaginas) {
        public static <T> PageView<T> of(Page<T> page) { return new PageView<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()); }
    }
    public record Summary(long pedidos, long pendentes, BigDecimal confirmado, long produtos, long unidades, long estoqueBaixo) {}
    public static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw ApiException.invalid(field + " é obrigatório e deve ter até " + max + " caracteres.");
        return value.trim();
    }
    public static int integer(Integer value, int min, int max, String field) {
        if (value == null || value < min || value > max) throw ApiException.invalid(field + " deve estar entre " + min + " e " + max + ".");
        return value;
    }
}
