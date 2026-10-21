# Sistema de Agendamento

[![Backend CI](https://github.com/Guilherme-Tonellidev/sistema-agendamento/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Guilherme-Tonellidev/sistema-agendamento/actions/workflows/backend-ci.yml)

Plataforma de agendamento de serviços (barbearia, clínica, salas): profissionais definem sua
disponibilidade, clientes reservam horários e o sistema garante que nunca existam dois
agendamentos sobrepostos para o mesmo profissional.

> Projeto em desenvolvimento. O roadmap abaixo mostra o que já está pronto.

## Stack

| Camada | Tecnologias |
| --- | --- |
| Back-end | Java 21, Spring Boot 4, Spring Data JPA, Bean Validation, MapStruct, Flyway |
| Documentação | OpenAPI (springdoc) e Swagger UI |
| Banco de dados | PostgreSQL 18 |
| Front-end | Angular *(etapa 5)* |
| Mensageria e cache | RabbitMQ *(etapa 7)*, Redis *(etapa 9)* |
| Testes | JUnit 5, AssertJ, Testcontainers |
| Entrega | Docker, GitHub Actions |

## Como rodar

**Pré-requisitos:** JDK 21 e Docker Desktop.

### Opção 1: só com Testcontainers (mais simples)

```bash
cd backend
./mvnw spring-boot:test-run
```

O Spring sobe um PostgreSQL em container automaticamente e o desliga ao encerrar a aplicação.

### Opção 2: com Docker Compose

```bash
docker compose up -d
cd backend
./mvnw spring-boot:run
```

Confira em <http://localhost:8080/actuator/health> e veja a documentação da API em
<http://localhost:8080/swagger-ui.html>.

### Testes

```bash
cd backend
./mvnw verify
```

> No Windows, use `mvnw.cmd` no lugar de `./mvnw` se não estiver no Git Bash.

## Deploy

A cada push na `main` com o CI verde, o workflow `release.yml` publica a imagem
`ghcr.io/guilherme-tonellidev/sistema-agendamento-api` (tags `latest` e o SHA do commit).

**Em qualquer servidor com Docker:**

```bash
cp .env.example .env   # troque POSTGRES_PASSWORD
docker compose -f docker-compose.prod.yml up -d --wait
```

Sobe a API e o PostgreSQL com healthcheck; as migrações rodam sozinhas na inicialização.
Use `docker compose -f docker-compose.prod.yml pull` para atualizar para a imagem mais nova.

**Em uma plataforma gerenciada** (Render, Railway, Fly.io...): rode a imagem acima (ou o
`backend/Dockerfile`) com `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` apontando para um PostgreSQL 18.
O health check fica em `/actuator/health/readiness`.

> A API ainda não tem autenticação (etapa 4). Não a exponha publicamente com dados reais antes disso.

## API

| Recurso | Endpoints |
| --- | --- |
| Serviços | `POST /api/servicos` · `GET /api/servicos` · `GET/PUT /api/servicos/{id}` · `PATCH /api/servicos/{id}/ativar` e `/inativar` |
| Profissionais | `POST /api/profissionais` · `GET /api/profissionais` · `GET/PUT /api/profissionais/{id}` · `PUT /api/profissionais/{id}/servicos` · `PATCH .../ativar` e `/inativar` |
| Clientes | `POST /api/clientes` · `GET /api/clientes` · `GET/PUT /api/clientes/{id}` |
| Disponibilidade | `GET/PUT /api/profissionais/{id}/jornada` · `POST/GET /api/profissionais/{id}/bloqueios` · `DELETE .../bloqueios/{bloqueioId}` · `GET /api/profissionais/{id}/horarios-livres?servicoId=&data=` |
| Agendamentos | `POST /api/agendamentos` · `GET /api/agendamentos` · `GET /api/agendamentos/{id}` · `PATCH .../{id}/cancelar`, `/reagendar`, `/concluir` e `/nao-compareceu` |

As listagens aceitam filtros (`nome`, `ativo`, `servicoId`, `status`, `de`, `ate`...) e paginação
(`page`, `size`, `sort`).

### Fluxo de uma reserva

1. Defina a jornada semanal do profissional (horário local, vários intervalos por dia) e, se
   precisar, bloqueie folgas e férias.
2. Consulte os horários livres de um dia para um serviço: o sistema desconta bloqueios, reservas
   ativas e horários que já passaram.
3. Reserve um desses horários. Um segundo pedido para o mesmo horário recebe `409`.

### Configuração

| Propriedade | Padrão | Para que serve |
| --- | --- | --- |
| `agendamento.fuso-horario` | `America/Sao_Paulo` | Fuso da jornada e das datas pedidas na API |
| `agendamento.intervalo-entre-horarios` | `PT15M` | Os horários oferecidos começam em múltiplos deste intervalo |
| `agendamento.janela-de-reserva` | `P90D` | Até quanto tempo à frente é possível reservar |
| `agendamento.antecedencia-minima-cancelamento` | `PT2H` | Prazo mínimo para o cliente cancelar |

Todas podem vir de variáveis de ambiente (ex.: `AGENDAMENTO_FUSO_HORARIO`).
Os erros seguem o padrão Problem Details (RFC 9457):

```json
{
  "status": 400,
  "title": "Dados inválidos",
  "detail": "Um ou mais campos estão inválidos.",
  "erros": [{ "campo": "email", "mensagem": "deve ser um endereço de e-mail bem formado" }]
}
```

## Decisões técnicas

- **Schema versionado com Flyway.** O Hibernate só valida (`ddl-auto: validate`); quem cria e
  altera tabelas são as migrações em `db/migration`.
- **Datas em UTC.** O banco e a aplicação trabalham em UTC, e a hora atual vem de um `Clock`
  injetado, o que permite testar regras de horário com datas fixas.
- **Conflito de horários garantido pelo banco.** Constraints de exclusão com `btree_gist` impedem
  que o profissional ou o cliente tenham duas reservas ativas sobrepostas, mesmo sob concorrência
  (há teste com requisições simultâneas). A aplicação valida antes só para dar uma mensagem clara.
- **Uma regra só para horários livres e reservas.** Um horário é reservável se e somente se aparece
  na lista de livres; o cálculo é uma classe pura, coberta por testes unitários.
- **Preço congelado na reserva.** O agendamento guarda o preço do momento; reajustes do serviço
  não mudam o que já foi combinado.
- **Erros padronizados.** Toda falha sai em Problem Details, com o status certo para cada caso:
  404 (não encontrado), 409 (duplicado ou edição concorrente), 422 (regra de negócio).
- **Concorrência otimista.** Cada registro tem uma versão; se duas pessoas editam ao mesmo tempo,
  a segunda recebe 409 em vez de apagar a alteração da primeira.
- **Inativar em vez de apagar.** Serviços e profissionais não são excluídos, para não quebrar o
  histórico de agendamentos.
- **Listagens sem N+1.** A lista de profissionais não carrega os serviços; o detalhe carrega tudo
  numa consulta só.
- **Testes com banco real.** Testcontainers sobe o mesmo PostgreSQL de produção, em vez de H2.
- **Configuração por ambiente.** O perfil `local` traz os valores de desenvolvimento; em
  produção, tudo vem de variáveis de ambiente (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) e os logs
  saem em JSON.
- **Imagem Docker enxuta.** Build em duas etapas, JAR extraído em camadas e execução sem usuário
  root.

## Roadmap

- [x] **0. Esqueleto:** Spring Boot, Flyway, Testcontainers, Docker e CI
- [x] **1. Cadastros:** profissionais, serviços e clientes
- [x] **2. Disponibilidade:** horários de trabalho, folgas e horários livres
- [x] **3. Reserva:** criação, cancelamento, reagendamento e conflito de horários
- [ ] **4. Segurança:** JWT com papéis e login com Google
- [ ] **5. Angular:** base, login e rotas protegidas
- [ ] **6. Fluxo de reserva no front-end** com FullCalendar
- [ ] **7. RabbitMQ:** eventos e e-mails de confirmação
- [ ] **8. Quartz:** lembretes 24h antes
- [ ] **9. Redis:** cache de horários e limite de tentativas de login
- [ ] **10. Painel do administrador** e histórico com Hibernate Envers
- [ ] **11. Deploy** com link público

## Autor

Guilherme Tonelli · [GitHub](https://github.com/Guilherme-Tonellidev)
