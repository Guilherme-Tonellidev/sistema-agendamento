# Front-end · Barbearia Tonelli

Angular 22 consumindo a API do [`backend`](../backend). Por enquanto: entrar, criar conta e
escolher horário; as demais telas chegam no decorrer da etapa 5.

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
├── core/           sessão, interceptors, guardas, contratos da API, datas e horário da casa
├── compartilhado/  vitrine, cabeçalho, ticket, marca e barra de carregamento
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
