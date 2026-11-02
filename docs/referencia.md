# Referência

Detalhes que não cabem no [README](../README.md): API, regras de reserva, avisos, configuração e deploy.

## API

A documentação interativa (Swagger UI) fica em <http://localhost:8080/swagger-ui.html> com a API no ar.

| Recurso | Endpoints |
| --- | --- |
| Serviços | `POST /api/servicos` · `GET /api/servicos` · `GET/PUT /api/servicos/{id}` · `PATCH /api/servicos/{id}/ativar` e `/inativar` |
| Profissionais | `POST /api/profissionais` · `GET /api/profissionais` · `GET/PUT /api/profissionais/{id}` · `PUT /api/profissionais/{id}/servicos` · `PATCH .../ativar` e `/inativar` |
| Clientes | `POST /api/clientes` · `GET /api/clientes` · `GET/PUT /api/clientes/{id}` |
| Disponibilidade | `GET/PUT /api/profissionais/{id}/jornada` · `POST/GET /api/profissionais/{id}/bloqueios` · `DELETE .../bloqueios/{bloqueioId}` · `GET /api/profissionais/{id}/horarios-livres?servicoId=&data=` · `GET .../horarios-livres/encaixe` |
| Autenticação | `POST /api/auth/cadastro` · `POST /api/auth/login` · `POST /api/auth/refresh` · `POST /api/auth/logout` · `GET /api/auth/eu` |
| Notificações | `GET /api/notificacoes` (as 20 mais recentes e quantas faltam ler) · `PATCH /api/notificacoes/{id}/ler` · `PATCH /api/notificacoes/ler-todas` |
| Agendamentos | `POST /api/agendamentos` · `POST /api/agendamentos/encaixe` · `GET /api/agendamentos` · `GET /api/agendamentos/{id}` · `PATCH .../{id}/cancelar`, `/reagendar`, `/concluir` e `/nao-compareceu` |

As listagens aceitam filtros (`nome`, `ativo`, `servicoId`, `status`, `de`, `ate`...) e paginação
(`page`, `size`, `sort`).

## Fluxo de uma reserva

1. Defina a jornada semanal do profissional (horário local, vários intervalos por dia) e, se
   precisar, bloqueie folgas e férias.
2. Consulte os horários livres de um dia para um serviço: o sistema desconta bloqueios, reservas
   ativas e horários que já passaram.
3. Reserve um desses horários (com pelo menos 1h30 de antecedência). Um segundo pedido para o
   mesmo horário recebe `409`.
4. Bloquear um período que já tem reservas também recebe `409`, com a lista dos agendamentos
   afetados: o profissional cancela ou remarca antes, e nenhum cliente perde o horário sem aviso.
5. Inativar um profissional ou serviço impede novas reservas, mas mantém as que já existem.
6. Quem chega sem reserva é encaixado pelo barbeiro só com o nome (e, se quiser, o telefone): sem
   antecedência mínima, mas com as mesmas regras de jornada, bloqueio e conflito.

## Avisos de reserva

Quem agiu já sabe: o aviso vai para a outra parte, e a reserva é a única exceção, porque o cliente
recebe um comprovante.

| Acontecimento | Cliente (e-mail) | Barbeiro (sino no cabeçalho) |
| --- | --- | --- |
| Reserva | Sempre, como comprovante | Sempre, exceto se ele mesmo marcou |
| Remarcação ou cancelamento | Só se não foi o cliente que fez | Só se não foi o barbeiro que fez |

Se o administrador agiu, os dois são avisados. Cliente sem e-mail (encaixe) nunca recebe e-mail.
Para ver funcionando, suba tudo pela opção 2 do [README](../README.md#como-rodar) com a API e o front no ar:

1. Entre como Carlos e reserve um horário com o Rafael.
2. Abra o Mailpit: o comprovante está lá.
3. Entre como Rafael: o sino mostra 1 não lida; clicar nela abre a Agenda no dia da reserva.
4. Cancele a reserva pela Agenda do Rafael: o e-mail de cancelamento chega ao Carlos.

A API aceita um motivo no cancelamento (campo `motivo` de `PATCH /api/agendamentos/{id}/cancelar`) e ele
aparece no e-mail e no sino; as telas não pedem esse campo.

## Lembretes

O cliente com e-mail recebe um lembrete 24 h antes do horário. Quem reservou com menos de 24 h de
antecedência recebe só um lembrete, 1 h antes do início (e o comprovante da reserva); se faltar menos
de 1 h, nenhum. Remarcar recalcula o lembrete, cancelar o cancela, e o barbeiro não recebe nada. O
lembrete sai até 1 minuto depois da hora calculada.

Para ver um lembrete sem esperar um dia, suba a API com `AGENDAMENTO_LEMBRETE_ANTECEDENCIA=PT2H` (no
PowerShell: `$env:AGENDAMENTO_LEMBRETE_ANTECEDENCIA = "PT2H"`) e reserve um horário que comece daqui a 2 h
ou mais (o mínimo para reservar é 1h30). O lembrete aparece no Mailpit 2 h antes do início.

## Configuração

| Propriedade | Padrão | Para que serve |
| --- | --- | --- |
| `agendamento.fuso-horario` | `America/Sao_Paulo` | Fuso da jornada e das datas pedidas na API |
| `agendamento.intervalo-entre-horarios` | `PT15M` | Os horários oferecidos começam em múltiplos deste intervalo |
| `agendamento.janela-de-reserva` | `P90D` | Até quanto tempo à frente é possível reservar |
| `agendamento.antecedencia-minima-reserva` | `PT1H30M` | Prazo mínimo entre a reserva e o início do horário |
| `agendamento.antecedencia-minima-cancelamento` | `PT2H` | Prazo mínimo para o cliente cancelar ou reagendar |
| `agendamento.lembrete.antecedencia` | `PT24H` | Quanto tempo antes do início o lembrete sai |
| `agendamento.lembrete.antecedencia-reserva-tardia` | `PT1H` | Quanto tempo antes do início sai o lembrete de quem reservou com menos de `antecedencia` (positiva e menor que ela) |
| `agendamento.dados-de-exemplo` | `false` (`true` no perfil `local`) | Cadastra os [dados de exemplo](../README.md#dados-de-exemplo) num banco sem serviços |
| `agendamento.notificacoes.remetente` | `Barbearia Tonelli <nao-responder@localhost>` | Remetente dos e-mails (`MAIL_REMETENTE`) |

Todas podem vir de variáveis de ambiente (ex.: `AGENDAMENTO_FUSO_HORARIO`).

E-mail e RabbitMQ (no perfil `local` já apontam para o Compose):

| Variável | Padrão | Para que serve |
| --- | --- | --- |
| `MAIL_HOST` | vazio | Servidor SMTP. Vazio: a API sobe e só registra no log quem receberia cada e-mail |
| `MAIL_PORT` | `587` | Porta do SMTP. Em produção a conexão exige STARTTLS |
| `MAIL_USUARIO` e `MAIL_SENHA` | vazios | Credenciais do SMTP |
| `MAIL_REMETENTE` | `Barbearia Tonelli <nao-responder@localhost>` | Remetente dos e-mails |
| `SPRING_RABBITMQ_HOST` | `localhost` | Endereço do RabbitMQ (porta, usuário e senha: `SPRING_RABBITMQ_PORT`, `_USERNAME` e `_PASSWORD`; padrão `5672`, `guest`, `guest`) |

Os erros seguem o padrão Problem Details (RFC 9457):

```json
{
  "status": 400,
  "title": "Dados inválidos",
  "detail": "Um ou mais campos estão inválidos.",
  "erros": [{ "campo": "email", "mensagem": "deve ser um endereço de e-mail bem formado" }]
}
```

## Deploy

A cada push na `main` com o CI verde, o workflow `release.yml` publica a imagem
`ghcr.io/guilherme-tonellidev/sistema-agendamento-api` (tags `latest` e o SHA do commit).

**Em qualquer servidor com Docker:**

```bash
cp .env.example .env   # troque POSTGRES_PASSWORD e preencha JWT_SEGREDO, ADMIN_EMAIL e ADMIN_SENHA
docker compose -f docker-compose.prod.yml up -d --wait
```

Sobe a API, o PostgreSQL e o RabbitMQ com healthcheck; as migrações rodam sozinhas na inicialização.
O RabbitMQ não publica portas: só a API o alcança. Para ver as filas, incluindo as de erros:
`docker compose -f docker-compose.prod.yml exec rabbitmq rabbitmqctl list_queues name messages`.
Use `docker compose -f docker-compose.prod.yml pull` para atualizar para a imagem mais nova.

**Em uma plataforma gerenciada** (Render, Railway, Fly.io...): rode a imagem acima (ou o
`backend/Dockerfile`) com `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` apontando para um PostgreSQL 18,
mais `JWT_SEGREDO` (obrigatório), `ADMIN_EMAIL`, `ADMIN_SENHA` e `CORS_ORIGENS`. O health check fica em
`/actuator/health/readiness`. Sem `JWT_SEGREDO` a aplicação nem sobe, de propósito. Os avisos
precisam também de um RabbitMQ (`SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_USERNAME` e
`SPRING_RABBITMQ_PASSWORD`) e de um servidor SMTP (`MAIL_*`, abaixo).

## Docker Compose de desenvolvimento

O PostgreSQL do Compose fica na porta 5433 do seu computador, para não disputar a 5432 com um PostgreSQL instalado direto no
sistema (dá para trocar com a variável `POSTGRES_PORT`). Todas as portas do Compose ficam presas ao `127.0.0.1`: o usuário `guest`
do RabbitMQ não é alcançável pela rede.
