package br.com.nexo.service;

import br.com.nexo.api.ApiException;
import br.com.nexo.api.Dtos;
import br.com.nexo.api.Dtos.*;
import br.com.nexo.domain.*;
import br.com.nexo.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final ProductRepository products;
    public OrderService(OrderRepository orders, ProductRepository products) { this.orders = orders; this.products = products; }

    @Transactional
    public OrderView create(OrderCreate input, String username) {
        String customer = Dtos.text(input.cliente(), 100, "Cliente");
        if (input.itens() == null || input.itens().isEmpty() || input.itens().size() > 50) throw ApiException.invalid("Informe entre 1 e 50 itens.");
        Map<Long, Integer> quantities = new TreeMap<>();
        for (ItemCreate item : input.itens()) {
            if (item == null || item.produtoId() == null || item.produtoId() < 1) throw ApiException.invalid("Produto inválido.");
            int quantity = Dtos.integer(item.quantidade(), 1, 10_000, "Quantidade");
            int combined = quantities.merge(item.produtoId(), quantity, Integer::sum);
            if (combined > 10_000) throw ApiException.invalid("Cada produto pode ter até 10.000 unidades no pedido.");
        }
        PurchaseOrder order = new PurchaseOrder(customer, username);
        for (var item : quantities.entrySet()) {
            Product product = products.findById(item.getKey()).orElseThrow(() -> ApiException.missing("Produto " + item.getKey() + " não encontrado."));
            order.addItem(product, item.getValue());
        }
        return OrderView.of(orders.saveAndFlush(order));
    }
    @Transactional(readOnly = true)
    public PageView<OrderView> list(OrderStatus status, String q, int page, int size) {
        CatalogService.validatePage(page, size);
        return PageView.of(orders.search(status, CatalogService.query(q), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(OrderView::of));
    }
    @Transactional(readOnly = true)
    public OrderView get(long id) { return OrderView.of(orders.findById(id).orElseThrow(() -> ApiException.missing("Pedido não encontrado."))); }

    @Transactional
    public OrderView confirm(long id) {
        // Primeiro bloqueia o pedido: repetir a mesma confirmação não baixa o saldo novamente.
        PurchaseOrder order = lockedOrder(id);
        if (order.getStatus() == OrderStatus.CONFIRMADO) return OrderView.of(order);
        if (order.getStatus() == OrderStatus.CANCELADO) throw ApiException.conflict("PEDIDO_CANCELADO", "Um pedido cancelado não pode ser confirmado.");
        Map<Long, Product> locked = lockProducts(order);
        // Valida todos os itens antes de alterar qualquer saldo.
        for (OrderItem item : order.getItems()) {
            Product product = locked.get(item.getProduct().getId());
            if (product.getStock() < item.getQuantity()) throw ApiException.conflict("ESTOQUE_INSUFICIENTE", "Estoque insuficiente de " + product.getName() + ". Disponível: " + product.getStock() + ".");
        }
        for (OrderItem item : order.getItems()) locked.get(item.getProduct().getId()).changeStock(-item.getQuantity());
        order.confirm();
        return OrderView.of(order);
    }
    @Transactional
    public OrderView cancel(long id) {
        PurchaseOrder order = lockedOrder(id);
        if (order.getStatus() == OrderStatus.CANCELADO) return OrderView.of(order);
        if (order.getStatus() == OrderStatus.CONFIRMADO) {
            Map<Long, Product> locked = lockProducts(order);
            for (OrderItem item : order.getItems()) {
                Product product = locked.get(item.getProduct().getId());
                if (product.getStock() > 1_000_000 - item.getQuantity()) throw ApiException.conflict("LIMITE_ESTOQUE", "A devolução excederia o limite de estoque.");
            }
            for (OrderItem item : order.getItems()) locked.get(item.getProduct().getId()).changeStock(item.getQuantity());
        }
        order.cancel();
        return OrderView.of(order);
    }
    @Transactional(readOnly = true)
    public Summary summary() {
        List<Product> all = products.findAll();
        BigDecimal revenue = orders.confirmedRevenue();
        return new Summary(orders.count(), orders.countByStatus(OrderStatus.PENDENTE), revenue == null ? BigDecimal.ZERO : revenue,
                all.size(), all.stream().mapToLong(Product::getStock).sum(), all.stream().filter(p -> p.getStock() < p.getMinimumStock()).count());
    }
    private PurchaseOrder lockedOrder(long id) { return orders.findLocked(id).orElseThrow(() -> ApiException.missing("Pedido não encontrado.")); }
    private Map<Long, Product> lockProducts(PurchaseOrder order) {
        Map<Long, Product> locked = new LinkedHashMap<>();
        // Todos os pedidos usam a mesma ordem de bloqueio para reduzir o risco de deadlock.
        order.getItems().stream().map(item -> item.getProduct().getId()).sorted().forEach(id ->
                locked.put(id, products.findLocked(id).orElseThrow(() -> ApiException.missing("Produto não encontrado."))));
        return locked;
    }
}
