package br.com.nexo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String customer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDENTE;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {}
    public PurchaseOrder(String customer, String createdBy) { this.customer = customer; this.createdBy = createdBy; }
    public void addItem(Product product, int quantity) { items.add(new OrderItem(this, product, quantity)); }
    public Long getId() { return id; }
    public String getCustomer() { return customer; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public List<OrderItem> getItems() { return items; }
    public void confirm() { status = OrderStatus.CONFIRMADO; }
    public void cancel() { status = OrderStatus.CANCELADO; }
}
