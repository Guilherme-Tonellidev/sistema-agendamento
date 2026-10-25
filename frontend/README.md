# Front-end · Barbearia Tonelli

Angular 22 consumindo a API do [`backend`](../backend). Telas: serviços, escolher horário, entrar,
criar conta, minhas reservas e a agenda do barbeiro (as contas de exemplo estão no
[README da raiz](../README.md#dados-de-exemplo)).

## Como rodar

Com a API no ar (veja o [README da raiz](../README.md)):

```bash
npm install
npm start
```

Abra <http://localhost:4200>. O `proxy.conf.json` encaminha `/api` para `localhost:8080`, então
front e API ficam na mesma origem e o cookie do refresh token funciona sem CORS.

| Comando         | Para que serve                     |
| --------------- | ---------------------------------- |
| `npm test`      | Testes unitários com Vitest        |
| `npm run build` | Build de produção na pasta `dist/` |

## Organização

```text
src/app/
├── core/           sessão, interceptors, guardas, contratos da API, datas, horário da casa e notificações
├── compartilhado/  vitrine, cabeçalho, sino, ticket, marca, barra de carregamento, formatos e telefone
└── paginas/        uma pasta por tela
```

## Decisões

- **Signals de ponta a ponta, sem zone.js.** Dados com `httpResource` e `rxResource`, formulários
  com Signal Forms e parâmetros da rota chegando como `input()`.
- **Token de acesso só em memória.** Ao abrir o site, a sessão volta pelo refresh token (cookie
  `HttpOnly`); num `401`, o interceptor renova uma vez e repete a chamada.
- **Datas no fuso da barbearia** (`America/Sao_Paulo`), não no do navegador.
- **A escolha de horário fica na URL:** sobrevive ao login e pode ser compartilhada.
- **"Sem preferência" de barbeiro:** o front junta os horários livres de cada um e oferece o
  primeiro livre em cada horário.
- **Trocar de dia não pisca a tela:** a grade anterior fica esmaecida até a nova chegar.
- **Prazos da casa na tela:** cancelar e remarcar só aparecem enquanto faltam mais de 2h; depois,
  a reserva diz para falar com a barbearia.
- **A agenda do barbeiro é a fila do dia, não um calendário:** quem está na cadeira, os próximos,
  os vãos livres e os bloqueios, recalculados a cada minuto. Encaixe e bloqueio saem dali mesmo.
- **WhatsApp com a mensagem pronta:** links `wa.me` para chamar o cliente ou avisar de um
  cancelamento, com o dia e o horário no texto.
- **Sino do barbeiro por consulta.** O serviço `Notificacoes` pergunta a `/api/notificacoes` a cada
  30 s com a aba à vista e ao voltar para ela, só enquanto há um barbeiro logado. Notificação nova
  também recarrega a fila da Agenda. Sem WebSocket: o aviso só precisa chegar em alguns segundos, e
  a consulta não exige um segundo canal em tempo real no back-end.
