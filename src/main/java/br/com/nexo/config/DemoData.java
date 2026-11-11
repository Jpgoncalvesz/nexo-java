package br.com.nexo.config;

import br.com.nexo.api.Dtos.*;
import br.com.nexo.domain.*;
import br.com.nexo.repository.*;
import br.com.nexo.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoData implements CommandLineRunner {
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderService service;
    private final PasswordEncoder passwords;
    @Value("${nexo.seed.enabled}") private boolean seed;
    @Value("${nexo.admin.username}") private String admin;
    @Value("${nexo.admin.password}") private String adminPassword;
    @Value("${nexo.seller.username}") private String seller;
    @Value("${nexo.seller.password}") private String sellerPassword;
    public DemoData(UserRepository users, ProductRepository products, OrderRepository orders, OrderService service, PasswordEncoder passwords) { this.users = users; this.products = products; this.orders = orders; this.service = service; this.passwords = passwords; }
    @Override @Transactional
    public void run(String... args) {
        createUser(admin, adminPassword, "ADMIN");
        if (!sellerPassword.isBlank()) createUser(seller, sellerPassword, "VENDEDOR");
        if (!seed || products.count() != 0 || orders.count() != 0) return;
        Product coffee = products.save(new Product("CAF-001", "Café especial 500 g", new BigDecimal("24.90"), 13, 3));
        Product milk = products.save(new Product("LEI-002", "Leite integral 1 L", new BigDecimal("5.90"), 8, 5));
        Product bread = products.save(new Product("PAO-003", "Pão de queijo 1 kg", new BigDecimal("27.90"), 1, 3));
        products.flush();
        var confirmed = service.create(new OrderCreate("Camila Alves", List.of(new ItemCreate(coffee.getId(), 1))), admin);
        service.confirm(confirmed.id());
        var cancelled = service.create(new OrderCreate("Gustavo Souza", List.of(new ItemCreate(milk.getId(), 3))), admin);
        service.cancel(cancelled.id());
        service.create(new OrderCreate("Rafael Lima", List.of(new ItemCreate(bread.getId(), 1))), admin);
        service.create(new OrderCreate("Mariana Costa", List.of(new ItemCreate(coffee.getId(), 2), new ItemCreate(milk.getId(), 1))), admin);
    }
    private void createUser(String username, String password, String role) {
        if (users.findByUsername(username).isPresent()) return;
        if (username.isBlank() || username.length() > 60 || password.length() < 12 || password.length() > 72) throw new IllegalStateException("Configure NEXO_ADMIN_PASSWORD (e NEXO_SELLER_PASSWORD, se utilizado) com 12 a 72 caracteres, ou execute o perfil demo localmente.");
        users.save(new AppUser(username, passwords.encode(password), role));
    }
}
