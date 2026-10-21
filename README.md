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

## API

| Recurso | Endpoints |
| --- | --- |
| Serviços | `POST /api/servicos` · `GET /api/servicos` · `GET/PUT /api/servicos/{id}` · `PATCH /api/servicos/{id}/ativar` e `/inativar` |
| Profissionais | `POST /api/profissionais` · `GET /api/profissionais` · `GET/PUT /api/profissionais/{id}` · `PUT /api/profissionais/{id}/servicos` · `PATCH .../ativar` e `/inativar` |
| Clientes | `POST /api/clientes` · `GET /api/clientes` · `GET/PUT /api/clientes/{id}` |

As listagens aceitam filtros (`nome`, `ativo`, `servicoId`...) e paginação (`page`, `size`, `sort`).
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
- **Conflito de horários garantido pelo banco** *(etapa 3)*. Uma constraint de exclusão com
  `btree_gist` impede reservas sobrepostas mesmo sob concorrência.
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
- [ ] **3. Reserva:** criação, cancelamento, reagendamento e conflito de horários
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
