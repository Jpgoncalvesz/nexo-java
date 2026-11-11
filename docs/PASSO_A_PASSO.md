# Como entender e apresentar o Nexo

## 1. O problema

Um comércio precisa registrar vendas sem perder o controle das quantidades disponíveis. Duas pessoas podem tentar vender a última unidade ao mesmo tempo. Confirmar um pedido também pode falhar quando apenas um dos seus produtos está em falta.

O Nexo guarda pedidos pendentes sem reservar unidades. Ao confirmar, verifica todos os produtos e grava a venda e a baixa do estoque na mesma transação.

## 2. Prepare o ambiente

Escolha Docker Compose para subir aplicativo e banco juntos, seguindo o README. Se utilizar o IntelliJ, importe `pom.xml`, configure Java 21 ou superior e disponibilize um PostgreSQL.

O arquivo `application.properties` lê endereço, usuário e senha do banco por variáveis de ambiente. O perfil `demo` adiciona credenciais locais conhecidas e exemplos. No primeiro início, `schema.sql` cria as tabelas e `DemoData` cria os usuários e os dados de demonstração. Reiniciar não recria os registros existentes.

## 3. Entenda as tabelas

- `app_users`: usuário, hash da senha e perfil.
- `products`: SKU único, nome, preço, saldo e estoque mínimo.
- `purchase_orders`: cliente, estado, instante e usuário que criou o pedido.
- `order_items`: produto, quantidade e preço unitário no momento da criação.

O preço é copiado para o item do pedido. Isso preserva o histórico da venda mesmo que o preço do produto mude depois. Valores monetários usam `BigDecimal` no Java e `NUMERIC(12,2)` no PostgreSQL.

## 4. Acompanhe uma requisição

1. A interface envia `POST /api/pedidos`, com cliente e itens.
2. O Spring Security verifica sessão, permissões e token CSRF.
3. `ApiController` recebe um DTO e chama `OrderService`.
4. O serviço valida os campos, reúne linhas repetidas do mesmo produto e consulta o catálogo.
5. As entidades são persistidas por `OrderRepository`; a resposta contém um `OrderView`, sem expor as entidades ou hashes de senha.

Explore `Dtos.java` para comparar o formato recebido com o formato devolvido.

## 5. Entenda a confirmação

Abra `OrderService.confirm()` e acompanhe:

1. `@Transactional` estabelece a fronteira da operação no banco.
2. `findLocked()` bloqueia a linha do pedido. Se já estiver confirmado, devolve o estado atual, sem uma segunda baixa.
3. Os produtos são bloqueados em ordem crescente de ID, para reduzir disputas de bloqueio entre pedidos.
4. O serviço valida o saldo de todos os itens.
5. Somente depois reduz os saldos e altera o pedido para `CONFIRMADO`.
6. O commit persiste tudo junto. Uma exceção de negócio desfaz as alterações.

`PESSIMISTIC_WRITE` corresponde a bloqueio de escrita de linha no banco. Outra transação que precise da mesma linha aguarda a liberação. A configuração limita a espera por bloqueio a cinco segundos e traduz o conflito em uma resposta tratável.

## 6. Entenda o cancelamento

- Pendente: muda para `CANCELADO` sem devolver unidades, pois não houve baixa.
- Confirmado: bloqueia os produtos, devolve as unidades e cancela na mesma transação.
- Já cancelado: retorna o pedido sem devolver unidades novamente.

Um pedido cancelado não pode voltar a ser confirmado; a API responde com conflito HTTP 409.

## 7. Entenda o login

O navegador primeiro solicita `GET /api/csrf`. O servidor entrega um token vinculado à sessão. A interface envia esse token no cabeçalho indicado ao fazer login e ao alterar dados.

O login utiliza `POST /api/auth/login` com formulário `username/password`. O Spring Security compara a senha com o hash BCrypt e mantém a identidade na sessão. A interface renova o token CSRF depois de login e logout. Não há token de autenticação armazenado em `localStorage`.

O administrador pode cadastrar produtos e repor estoque. O vendedor pode consultar e operar pedidos. Essas permissões são verificadas no servidor, além de refletidas na interface.

## 8. Execute e interprete os testes

Suba o banco de testes pelo `compose.test.yaml` e execute `mvn verify`. O banco usado deve terminar em `_test`, pois a suíte limpa as tabelas de negócio.

Em `twoConcurrentOrdersCannotSellTheSameLastUnit`, duas tarefas são liberadas juntas para confirmar pedidos diferentes sobre um produto com saldo 1. O resultado esperado é uma confirmação, um conflito e saldo final zero.

Em `insufficientSecondItemDoesNotPartiallyDeductFirstItem`, um item tem saldo e outro não. O resultado esperado é HTTP 409, pedido ainda pendente e ambos os saldos intactos.

Confira `target/surefire-reports` e o resumo `Tests run` antes de afirmar que os testes passaram. Ter testes escritos ou compilados não comprova execução bem-sucedida.

## 9. Faça uma demonstração de três minutos

1. Entre como administrador e mostre os produtos.
2. Crie um pedido com dois itens.
3. Confirme e mostre a redução de saldo no estoque.
4. Cancele e mostre a devolução.
5. Tente confirmar uma quantidade maior que o saldo e explique o conflito.
6. Abra **API e regras** e execute uma consulta real.
7. Mostre `OrderService`, o teste concorrente e o relatório real dos testes.

## 10. Publique o código no GitHub

O projeto precisa de um repositório novo, por exemplo `nexo-java`. Não envie `.env`, `.local`, `target` ou arquivos com dados de clientes. O `.gitignore` já exclui os arquivos locais previstos.

Caso publique manualmente com GitHub CLI autenticado, execute dentro da pasta do projeto:

```sh
git init -b main
git add .
git commit -m "Implementa Nexo com pedidos, estoque e autenticação"
gh auth status
gh repo create nexo-java --public --source . --remote origin --push
```

Se o repositório já tiver sido inicializado ou publicado, não repita sua criação: confira `git status` e `git remote -v`. A publicação efetiva deve ser confirmada pelo link remoto e pelos arquivos presentes no GitHub.

Abra a aba **Actions** para acompanhar o workflow Java e PostgreSQL. Um arquivo de workflow preparado não significa que o pipeline já executou.

GitHub Pages não executa Java. Publicar o repositório torna o código acessível; disponibilizar a aplicação completa exige executar a imagem Docker em um servidor e configurar o banco.

## 11. Prepare-se para a entrevista

Explique por que o preço fica no item, por que a confirmação é transacional, o que acontece com duas vendas concorrentes, como repetir uma confirmação se torna seguro, por que CSRF continua habilitado e quais limitações você priorizaria em uma próxima versão.

Apresente o uso da IA com precisão e estude cada uma dessas decisões antes de usar o projeto como evidência de domínio técnico.
