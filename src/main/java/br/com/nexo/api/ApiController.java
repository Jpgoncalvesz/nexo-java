package br.com.nexo.api;

import br.com.nexo.api.Dtos.*;
import br.com.nexo.domain.OrderStatus;
import br.com.nexo.service.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final CatalogService catalog;
    private final OrderService orders;
    private final Environment environment;
    public ApiController(CatalogService catalog, OrderService orders, Environment environment) { this.catalog = catalog; this.orders = orders; this.environment = environment; }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }
    @GetMapping("/info") public Map<String, Object> info() { return Map.of("nome", "Nexo", "demo", environment.acceptsProfiles(Profiles.of("demo"))); }
    @GetMapping("/csrf") public Map<String, String> csrf(CsrfToken token) { return Map.of("headerName", token.getHeaderName(), "token", token.getToken()); }
    @GetMapping("/me") public Map<String, Object> me(Authentication authentication) { return Map.of("usuario", authentication.getName(), "perfis", authentication.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).toList()); }
    @GetMapping("/resumo") public Summary summary() { return orders.summary(); }
    @GetMapping("/produtos") public PageView<ProductView> products(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) { return catalog.list(q, pagina, tamanho); }
    @PostMapping("/produtos") @ResponseStatus(HttpStatus.CREATED)
    public ProductView createProduct(@RequestBody ProductCreate input) { return catalog.create(input); }
    @PostMapping("/produtos/{id}/reposicoes") public ProductView replenish(@PathVariable long id, @RequestBody StockAdd input) { return catalog.replenish(id, input); }
    @GetMapping("/pedidos") public PageView<OrderView> orders(@RequestParam(required = false) OrderStatus status, @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) { return orders.list(status, q, pagina, tamanho); }
    @GetMapping("/pedidos/{id}") public OrderView order(@PathVariable long id) { return orders.get(id); }
    @PostMapping("/pedidos") @ResponseStatus(HttpStatus.CREATED)
    public OrderView createOrder(@RequestBody OrderCreate input, Principal user) { return orders.create(input, user.getName()); }
    @PostMapping("/pedidos/{id}/confirmacao") public OrderView confirm(@PathVariable long id) { return orders.confirm(id); }
    @PostMapping("/pedidos/{id}/cancelamento") public OrderView cancel(@PathVariable long id) { return orders.cancel(id); }
}
