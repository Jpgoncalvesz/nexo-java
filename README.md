# Nexo — pedidos e estoque

Aplicação de portfólio em **Java 21+, Spring Boot 4.1.1 e PostgreSQL 18**. Uma venda só é confirmada se houver saldo para todos os itens. Pedido e estoque são atualizados na mesma transação, com bloqueios de linha para controlar confirmações concorrentes.

Interface em HTML, CSS e JavaScript, servida pelo próprio Spring Boot. As telas **Pedidos**, **Estoque** e **API e regras** usam a API real. Esta implementação é diferente do protótipo inicial com dados simulados.

## Funcionalidades

- Autenticação por sessão, senhas com BCrypt, CSRF habilitado e cookies HttpOnly.
- Administrador cadastra produtos e registra reposições; vendedor consulta e opera pedidos.
- Pedidos com vários itens, preço histórico, filtros e paginação.
- Confirmação e cancelamento idempotentes: repetir a operação não movimenta o estoque duas vezes.
- Cancelar uma venda confirmada devolve as unidades; cancelar um pedido pendente mantém o saldo.
- Validação de quantidade, preço, SKU e limites de estoque; erros HTTP com código e mensagem.
- Contrato [OpenAPI](src/main/resources/static/openapi.yaml), Docker Compose e pipeline de integração.

## Executar com Docker

Pré-requisito: Docker com Compose disponível e portas locais 5432 e 8080 livres.

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose logs -f app
```

No Linux/macOS, use `cp .env.example .env` no primeiro comando. Abra **http://localhost:8080** após a aplicação iniciar.

| Perfil de demonstração | Usuário | Senha padrão local |
|---|---|---|
| Administrador | admin | AdminDemo!2026 |
| Vendedor | vendedor | VendedorDemo!2026 |

Essas credenciais pertencem ao perfil `demo`, para desenvolvimento local. As variáveis em `.env` permitem substituí-las. O arquivo `.env` não é versionado. Senhas alteradas em variáveis não redefinem usuários já existentes no banco.

Para parar e preservar os dados: `docker compose down`. O banco é mantido em um volume Docker.

## Executar pelo IntelliJ ou Maven

1. Instale Java 21+ e abra `pom.xml` como projeto Maven.
2. Copie `.env.example` para `.env` e suba somente o banco: `docker compose up -d db`.
3. Nas configurações de execução, defina `DB_PASSWORD=nexo_local_development` e `SPRING_PROFILES_ACTIVE=demo`.
4. Execute `NexoApplication.main()`.

Com Maven no PATH:

```powershell
$env:DB_PASSWORD = 'nexo_local_development'
$env:SPRING_PROFILES_ACTIVE = 'demo'
mvn spring-boot:run
```

O Maven não carrega `.env` automaticamente. No Windows, `scripts/maven.ps1` também procura o Maven incluído no IntelliJ:

```powershell
.\scripts\maven.ps1 spring-boot:run
```

Também é possível utilizar um PostgreSQL já instalado: crie um banco vazio `nexo`, configure `DB_URL`, `DB_USER` e `DB_PASSWORD` e execute a aplicação. `schema.sql` cria a estrutura inicial, e o Hibernate valida a correspondência com as entidades.

## Executar os testes

Os testes utilizam um banco descartável separado, cujo nome deve terminar em `_test`.

```powershell
docker compose -f compose.test.yaml up -d --wait
mvn verify
docker compose -f compose.test.yaml down
```

No Windows, pode substituir `mvn verify` por `.\scripts\maven.ps1 verify`.

Para usar outro servidor, defina `NEXO_TEST_DB_URL`, `NEXO_TEST_DB_USER` e `NEXO_TEST_DB_PASSWORD`. **A suíte limpa os dados de negócio desse banco antes de cada teste.** Nunca aponte essas variáveis para dados reais.

A suíte contém 14 cenários: login válido/inválido, sessão, permissões, CSRF, preços históricos, saldo insuficiente, atomicidade, repetição de operações, devolução, itens duplicados, reposição, filtros e disputa pela última unidade. O workflow de GitHub Actions provisiona PostgreSQL e executa `mvn verify`.

Consulte [VALIDACAO.md](docs/VALIDACAO.md) para distinguir verificações executadas das ainda pendentes.

## Estrutura

| Caminho | Responsabilidade |
|---|---|
| `api` | Rotas HTTP, DTOs, validações de entrada e erros |
| `service` | Regras de negócio e transações |
| `domain` | Entidades e estados persistidos |
| `repository` | Consultas JPA e bloqueios de linha |
| `config` | Segurança e inicialização de usuários/dados |
| `static` | Interface e contrato OpenAPI |
| `StockWorkflowTest` | Integração com PostgreSQL real |

Leia o [guia passo a passo](docs/PASSO_A_PASSO.md) para entender o projeto, demonstrá-lo e publicar o repositório.

## Publicação e hospedagem

O GitHub guarda o código e pode executar o pipeline. **GitHub Pages é hospedagem estática e não executa este servidor Java.** Para disponibilizar a aplicação completa na internet, execute o contêiner em uma hospedagem com suporte a Java/Docker e forneça um PostgreSQL persistente.

Para esse ambiente, não ative o perfil `demo`; configure um administrador inicial com `NEXO_ADMIN_USERNAME` e `NEXO_ADMIN_PASSWORD`, além das variáveis do banco. A senha inicial precisa ter de 12 a 72 caracteres. Use HTTPS e `COOKIE_SECURE=true`. `SERVER_ADDRESS=0.0.0.0` é configurado na imagem Docker; a execução local sem Docker fica limitada ao loopback por padrão.

## Decisões e limites

- Aplicação única com separação por responsabilidade, adequada ao escopo.
- Sessões de login ficam na memória do servidor; reiniciá-lo exige novo login.
- O catálogo da interface é carregado integralmente; catálogos grandes precisam de busca/paginação no seletor de itens.
- O resumo representa pedidos de todos os usuários; esta versão não implementa organizações independentes.
- `schema.sql` é o esquema inicial, não um sistema de migrações incrementais. Evoluções de produção exigem migrações versionadas.
- Esta versão não emite nota fiscal, não processa pagamentos e não faz conciliação financeira.
- Não há processo de redefinição de senha ou gestão de usuários pela interface nesta versão.
- Construído com assistência de IA. A documentação explicita as decisões e o estado real de validação.

Referências: [Spring Boot](https://docs.spring.io/spring-boot/), [CSRF no Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages).
