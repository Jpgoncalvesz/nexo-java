package br.com.nexo.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String sku;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Column(nullable = false)
    private int stock;
    @Column(name = "minimum_stock", nullable = false)
    private int minimumStock;

    protected Product() {}
    public Product(String sku, String name, BigDecimal price, int stock, int minimumStock) {
        this.sku = sku; this.name = name; this.price = price;
        this.stock = stock; this.minimumStock = minimumStock;
    }
    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public int getMinimumStock() { return minimumStock; }
    public void changeStock(int delta) {
        int next = Math.addExact(stock, delta);
        if (next < 0 || next > 1_000_000) throw new IllegalArgumentException("Saldo fora do limite permitido.");
        stock = next;
    }
}
