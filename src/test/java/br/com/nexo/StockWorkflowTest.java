package br.com.nexo;

import br.com.nexo.api.ApiException;
import br.com.nexo.api.Dtos.*;
import br.com.nexo.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;

    @BeforeEach void cleanBusinessData() {
        // A suíte usa exclusivamente nexo_test, nunca o banco do aplicativo.
        assertTrue(jdbc.queryForObject("select current_database()", String.class).endsWith("_test"), "Use um banco cujo nome termine em _test.");
        jdbc.execute("TRUNCATE order_items, purchase_orders, products RESTART IDENTITY CASCADE");
    }
    @Test void anonymousCannotReadBusinessData() throws Exception {
        mvc.perform(get("/api/produtos")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("AUTENTICACAO_NECESSARIA"));
    }
    @Test void loginCreatesAuthenticatedSession() throws Exception {
        var tokenRequest = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
        var token = json.readTree(tokenRequest.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) tokenRequest.getRequest().getSession();
        var result = mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asText(), token.get("token").asText())
                        .param("username", "admin").param("password", "AdminTeste!2026"))
                .andExpect(status().isNoContent()).andReturn();
        mvc.perform(get("/api/me").session((MockHttpSession) result.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuario").value("admin")).andExpect(jsonPath("$.perfis[0]").value("ADMIN"));
    }
    @Test void invalidPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "admin").param("password", "senha-incorreta"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }
    @Test void sellerCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/produtos").with(user("vendedor").roles("VENDEDOR")).with(csrf()).contentType("application/json")
                        .content("{\"sku\":\"TEST\",\"nome\":\"Teste\",\"preco\":10,\"estoque\":1,\"estoqueMinimo\":1}"))
                .andExpect(status().isForbidden());
    }
    @Test void writeWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/produtos").with(user("admin").roles("ADMIN")).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
    @Test void moneyUsesHistoricalUnitPriceAndStockWaitsForConfirmation() throws Exception {
        long p = product("CAFE", "Café", "24.90", 10);
        long id = order("Cliente", new ItemCreate(p, 3));
        assertEquals(10, stock(p));
        jdbc.update("update products set price=30.00 where id=?", p);
        mvc.perform(get("/api/pedidos/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(74.70))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(24.90));
    }
    @Test void confirmationAndCancellationAreIdempotent() throws Exception {
        long p = product("CAFE", "Café", "24.90", 10);
        long id = order("Cliente", new ItemCreate(p, 2));
        confirm(id, 200); assertEquals(8, stock(p));
        confirm(id, 200); assertEquals(8, stock(p));
        cancel(id); assertEquals(10, stock(p));
        cancel(id); assertEquals(10, stock(p));
        confirm(id, 409); assertEquals(10, stock(p));
    }
    @Test void cancellingPendingOrderDoesNotIncreaseStock() throws Exception {
        long p = product("LEITE", "Leite", "5.90", 4);
        long id = order("Cliente", new ItemCreate(p, 2));
        cancel(id); assertEquals(4, stock(p));
    }
    @Test void insufficientSecondItemDoesNotPartiallyDeductFirstItem() throws Exception {
        long first = product("CAFE", "Café", "24.90", 10);
        long second = product("PAO", "Pão", "27.90", 0);
        long id = order("Cliente", new ItemCreate(first, 5), new ItemCreate(second, 1));
        mvc.perform(post("/api/pedidos/" + id + "/confirmacao").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ESTOQUE_INSUFICIENTE"));
        assertEquals(10, stock(first)); assertEquals(0, stock(second));
        assertEquals("PENDENTE", jdbc.queryForObject("select status from purchase_orders where id=?", String.class, id));
    }
    @Test void duplicateProductLinesAreCombined() throws Exception {
        long p = product("CAFE", "Café", "10.00", 10);
        long id = order("Cliente", new ItemCreate(p, 2), new ItemCreate(p, 3));
        confirm(id, 200); assertEquals(5, stock(p));
        assertEquals(1, jdbc.queryForObject("select count(*) from order_items where order_id=?", Integer.class, id));
    }
    @Test void invalidQuantityPriceAndDuplicateSkuAreRejected() throws Exception {
        long p = product("CAFE", "Café", "24.90", 10);
        mvc.perform(post("/api/pedidos").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(new OrderCreate("Cliente", List.of(new ItemCreate(p, -1))))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/produtos").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(new ProductCreate("OUTRO", "Outro", new BigDecimal("1.001"), 1, 0))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/produtos").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(new ProductCreate("cafe", "Outro", new BigDecimal("1.00"), 1, 0))))
                .andExpect(status().isConflict());
    }
    @Test void replenishmentChangesPersistentStockAndSellerCanSell() throws Exception {
        long p = product("CAFE", "Café", "24.90", 0);
        mvc.perform(post("/api/produtos/" + p + "/reposicoes").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"quantidade\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estoque").value(3));
        long id = order("Cliente", new ItemCreate(p, 1));
        mvc.perform(post("/api/pedidos/" + id + "/confirmacao").with(user("vendedor").roles("VENDEDOR")).with(csrf()))
                .andExpect(status().isOk());
        assertEquals(2, stock(p));
    }
    @Test void filtersPaginationAndMissingRecordsHaveDefinedResponses() throws Exception {
        long p = product("CAFE", "Café", "24.90", 10);
        long id = order("Maria", new ItemCreate(p, 1));
        order("João", new ItemCreate(p, 1));
        confirm(id, 200);
        mvc.perform(get("/api/pedidos?status=CONFIRMADO&q=Maria&tamanho=1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.itens[0].cliente").value("Maria"));
        mvc.perform(get("/api/pedidos/9999").with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/produtos?tamanho=101").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }
    @Test void twoConcurrentOrdersCannotSellTheSameLastUnit() throws Exception {
        long p = product("ULTIMO", "Última unidade", "10.00", 1);
        long first = order("Primeiro", new ItemCreate(p, 1));
        long second = order("Segundo", new ItemCreate(p, 1));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> a = pool.submit(() -> concurrentConfirm(first, start));
            Future<Integer> b = pool.submit(() -> concurrentConfirm(second, start));
            start.countDown();
            List<Integer> results = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(code -> code == 200).count());
            assertEquals(1, results.stream().filter(code -> code == 409).count());
        }
        assertEquals(0, stock(p));
        assertEquals(1, jdbc.queryForObject("select count(*) from purchase_orders where status='CONFIRMADO'", Integer.class));
    }
    private int concurrentConfirm(long id, CountDownLatch start) throws InterruptedException {
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sincronização dos testes expirou.");
        try { orders.confirm(id); return 200; } catch (ApiException error) { return error.status().value(); }
    }
    private long product(String sku, String name, String price, int stock) throws Exception {
        var result = mvc.perform(post("/api/produtos").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(new ProductCreate(sku, name, new BigDecimal(price), stock, 1))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    private long order(String customer, ItemCreate... items) throws Exception {
        var result = mvc.perform(post("/api/pedidos").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(new OrderCreate(customer, List.of(items)))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    private void confirm(long id, int expected) throws Exception { mvc.perform(post("/api/pedidos/" + id + "/confirmacao").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().is(expected)); }
    private void cancel(long id) throws Exception { mvc.perform(post("/api/pedidos/" + id + "/cancelamento").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk()); }
    private int stock(long id) { return jdbc.queryForObject("select stock from products where id=?", Integer.class, id); }
}
