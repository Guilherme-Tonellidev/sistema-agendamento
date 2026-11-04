# Referência

Detalhes que não cabem no [README](../README.md): API, regras de reserva, avisos, configuração e deploy.

## API

A documentação interativa (Swagger UI) fica em <http://localhost:8080/swagger-ui.html> com a API no ar. Ela só existe em
desenvolvimento: o perfil `prod` desliga o Swagger UI e o `/v3/api-docs`.

| Recurso | Endpoints |
| --- | --- |
| Serviços | `POST /api/servicos` · `GET /api/servicos` · `GET/PUT /api/servicos/{id}` · `PATCH /api/servicos/{id}/ativar` e `/inativar` |
| Profissionais | `POST /api/profissionais` · `GET /api/profissionais` · `GET/PUT /api/profissionais/{id}` · `PUT /api/profissionais/{id}/servicos` · `PATCH .../ativar` e `/inativar` |
| Clientes | `POST /api/clientes` · `GET /api/clientes` · `GET/PUT /api/clientes/{id}` |
| Disponibilidade | `GET/PUT /api/profissionais/{id}/jornada` · `POST/GET /api/profissionais/{id}/bloqueios` · `DELETE .../bloqueios/{bloqueioId}` · `GET /api/profissionais/{id}/horarios-livres?servicoId=&data=` · `GET .../horarios-livres/encaixe` |
| Autenticação | `POST /api/auth/cadastro` · `POST /api/auth/login` · `POST /api/auth/refresh` · `POST /api/auth/logout` · `GET /api/auth/eu` |
| Notificações | `GET /api/notificacoes` (as 20 mais recentes e quantas faltam ler) · `PATCH /api/notificacoes/{id}/ler` · `PATCH /api/notificacoes/ler-todas` |
| Agendamentos | `POST /api/agendamentos` · `POST /api/agendamentos/encaixe` · `GET /api/agendamentos` · `GET /api/agendamentos/{id}` · `PATCH .../{id}/cancelar`, `/reagendar`, `/concluir` e `/nao-compareceu` |

`POST /api/auth/login` responde `429` (com `Retry-After`, em segundos) depois de 5 tentativas em 15 minutos para o mesmo
e-mail e a mesma origem; um login correto zera a contagem. A contagem fica no Redis e vale entre as instâncias da API;
com o Redis fora do ar, o login segue e cada instância conta por conta própria, na memória. A origem é o endereço da
conexão: atrás de um proxy reverso ela é a do proxy, então o servidor precisa confiar no `X-Forwarded-For` dele (e só
nele) ou o limite passa a valer por e-mail para todos. O `docker-compose.prod.yml` já faz isso (`SERVER_FORWARD_HEADERS_STRATEGY=native`)
e publica a porta da API só em `127.0.0.1`: ponha o proxy reverso, com TLS, no mesmo host. Fora do Compose, repita as duas coisas.

As listagens aceitam filtros (`nome`, `ativo`, `servicoId`, `status`, `de`, `ate`...) e paginação
(`page`, `size`, `sort`). Nas listagens públicas de serviços e profissionais, quem não é administrador só vê itens
ativos (o filtro `ativo=false` é ignorado) e só pode ordenar por `nome` (e, nos serviços, `duracaoMinutos` e `preco`);
qualquer outro campo recebe `400`.

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

## Semana do administrador

A rota `/semana`, só para o administrador, mostra de segunda a domingo os agendamentos de todos os barbeiros num
calendário, com uma cor por barbeiro. A legenda liga e desliga cada barbeiro, e "Mostrar cancelados" traz de volta os
cancelamentos.

| Quero... | Como |
| --- | --- |
| Marcar | Clique num horário vazio e escolha barbeiro, serviço, horário e cliente: um já cadastrado ou um novo, só com nome e telefone (encaixe). |
| Ver o agendamento | Clique no bloco: dados do cliente, link do WhatsApp, cancelar, concluir e marcar falta (estes dois só depois do início) e o histórico de alterações. |
| Remarcar | Arraste o bloco para outro horário ou use o botão "Remarcar" dos detalhes. Só o agendamento confirmado muda de horário; o barbeiro e a duração ficam como estão. |

O administrador não tem trava de tempo: marca e remarca no passado e além da janela de reserva (90 dias por padrão), e
remarca ou cancela agendamentos confirmados que já começaram. Jornada, bloqueio, alinhamento aos 15 minutos e conflito
de horário continuam valendo. Quando o servidor recusa um arraste, o bloco volta ao lugar e o motivo aparece no topo da
página.

Para oferecer horários passados e além da janela, a tela chama `GET /api/profissionais/{id}/horarios-livres/encaixe` com
`semLimite=true`. O parâmetro é só do administrador: profissional e cliente recebem `403`. A linha do tempo dos detalhes
lê `GET /api/agendamentos/{id}/historico` (só administrador).

## Cache de horários

A consulta de horários livres (`GET .../horarios-livres` e `.../horarios-livres/encaixe`) guarda no Redis os horários de um
profissional num dia, por 2 minutos. Serviços da mesma duração dividem a entrada. O prazo de antecedência fica de fora da
entrada e é aplicado a cada leitura, então "daqui a pouco" e o encaixe sempre valem.

Reservar, cancelar, reagendar, concluir, marcar falta, bloquear, remover um bloqueio e trocar a jornada mudam a versão do
profissional depois do commit, e as entradas antigas deixam de ser lidas. A versão é um token aleatório que vale 1 hora;
se o Redis a perder, a API cria uma nova, então uma entrada antiga nunca volta.

A reserva não lê o cache: ela confere jornada, bloqueios e conflitos no banco. Sem o Redis, a consulta vai direto ao
banco e o log registra um aviso por minuto. Depois de uma falha, a API só tenta o Redis de novo a cada 5 segundos, uma
chamada por vez, para um Redis inalcançável não fazer cada requisição esperar o timeout de 300 ms; só as que chegam
juntas na primeira falha esperam, uma única vez.

O que o cache pode errar: se o Redis estiver fora do ar, ou tiver acabado de falhar, justo quando a agenda muda, a troca
da versão se perde e a tela mostra o horário velho por até 2 minutos. Quem tentar reservá-lo recebe a recusa do banco
(`409` ou `422`). Se recriar ou restaurar o banco com a API no ar, esvazie o Redis junto (`FLUSHDB`).

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
| `agendamento.cache-horarios.ttl` | `PT2M` | Por quanto tempo a consulta de horários livres de um dia fica no Redis (positivo) |
| `agendamento.lembrete.antecedencia` | `PT24H` | Quanto tempo antes do início o lembrete sai |
| `agendamento.lembrete.antecedencia-reserva-tardia` | `PT1H` | Quanto tempo antes do início sai o lembrete de quem reservou com menos de `antecedencia` (positiva e menor que ela) |
| `agendamento.dados-de-exemplo` | `false` (`true` no perfil `local`) | Cadastra os [dados de exemplo](../README.md#dados-de-exemplo) num banco sem serviços |
| `agendamento.notificacoes.remetente` | `Barbearia Tonelli <nao-responder@localhost>` | Remetente dos e-mails (`MAIL_REMETENTE`) |

Todas podem vir de variáveis de ambiente (ex.: `AGENDAMENTO_FUSO_HORARIO`).

E-mail, RabbitMQ e Redis (no perfil `local` já apontam para o Compose):

| Variável | Padrão | Para que serve |
| --- | --- | --- |
| `MAIL_HOST` | vazio | Servidor SMTP. Vazio: a API sobe e só registra no log quem receberia cada e-mail |
| `MAIL_PORT` | `587` | Porta do SMTP. Em produção a conexão exige STARTTLS |
| `MAIL_USUARIO` e `MAIL_SENHA` | vazios | Credenciais do SMTP |
| `MAIL_REMETENTE` | `Barbearia Tonelli <nao-responder@localhost>` | Remetente dos e-mails |
| `SPRING_RABBITMQ_HOST` | `localhost` | Endereço do RabbitMQ (porta, usuário e senha: `SPRING_RABBITMQ_PORT`, `_USERNAME` e `_PASSWORD`; padrão `5672`, `guest`, `guest`) |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Endereço do Redis (porta: `SPRING_DATA_REDIS_PORT`, padrão `6379`) |
| `SPRING_DATA_REDIS_PASSWORD` | vazio | Senha do Redis. Em produção a API a recebe do `REDIS_PASSWORD` do Compose |
| `SPRING_DATA_REDIS_SSL_ENABLED` | `false` | Liga o TLS na conexão com o Redis. Obrigatório com um Redis fora da rede interna (serviço gerenciado) |
| `REDIS_PASSWORD` | sem padrão | Só no `docker-compose.prod.yml` e obrigatória: a senha que o Compose define no Redis e entrega à API |

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
cp .env.example .env   # troque POSTGRES_PASSWORD e preencha JWT_SEGREDO, ADMIN_EMAIL, ADMIN_SENHA, RABBITMQ_DEFAULT_PASS e REDIS_PASSWORD
docker compose -f docker-compose.prod.yml up -d --wait
```

Sobe a API, o PostgreSQL, o RabbitMQ e o Redis com healthcheck; as migrações rodam sozinhas na inicialização.
O RabbitMQ e o Redis não publicam portas: só a API os alcança. Para ver as filas, incluindo as de erros:
`docker compose -f docker-compose.prod.yml exec rabbitmq rabbitmqctl list_queues name messages`.
Use `docker compose -f docker-compose.prod.yml pull` para atualizar para a imagem mais nova.

**Em uma plataforma gerenciada** (Render, Railway, Fly.io...): rode a imagem acima (ou o
`backend/Dockerfile`) com `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` apontando para um PostgreSQL 18,
mais `JWT_SEGREDO` (obrigatório), `ADMIN_EMAIL`, `ADMIN_SENHA` e `CORS_ORIGENS`. O health check fica em
`/actuator/health/readiness`. Sem `JWT_SEGREDO` a aplicação nem sobe, de propósito. Ela também não tem perfil padrão: a imagem define
`SPRING_PROFILES_ACTIVE=prod`, e o perfil `local` nunca deve ser ativado em produção, porque cadastra contas com senhas
conhecidas. Os avisos
precisam também de um RabbitMQ (`SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_USERNAME` e
`SPRING_RABBITMQ_PASSWORD`), de um servidor SMTP (`MAIL_*`, abaixo) e de um Redis (`SPRING_DATA_REDIS_HOST` e, se ele
tiver senha, `SPRING_DATA_REDIS_PASSWORD`). Sem o Redis a API segue de pé: o limite de login passa a contar só na memória
de cada instância, e a consulta de horários vai direto ao banco.

Com um Redis gerenciado, ou qualquer um que a API alcance pela internet, defina `SPRING_DATA_REDIS_SSL_ENABLED=true`
(e use a porta TLS do serviço). Sem isso a senha do Redis e as contagens de login, que levam um hash do e-mail, viajam
sem criptografia. Só o Redis do `docker-compose.prod.yml`, que fica na rede interna do Compose, dispensa o TLS.

## Docker Compose de desenvolvimento

O PostgreSQL do Compose fica na porta 5433 do seu computador, para não disputar a 5432 com um PostgreSQL instalado direto no
sistema (dá para trocar com a variável `POSTGRES_PORT`). Todas as portas do Compose ficam presas ao `127.0.0.1`: o usuário `guest`
do RabbitMQ e o Redis, que lá não tem senha, não são alcançáveis pela rede.
