import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { Sessao } from './sessao';

/** Rotas que abrem ou renovam a sessão: sem token, que vencido faria o próprio refresh dar 401. */
const ROTAS_DA_SESSAO = new Set([
  '/api/auth/login',
  '/api/auth/cadastro',
  '/api/auth/refresh',
  '/api/auth/logout',
]);

/** Põe o token nas chamadas à API e, num 401, renova a sessão uma vez e repete a chamada. */
export const autenticacaoInterceptor: HttpInterceptorFn = (requisicao, proximo) => {
  if (!requisicao.url.startsWith('/api/') || ROTAS_DA_SESSAO.has(requisicao.url)) {
    return proximo(requisicao);
  }
  const sessao = inject(Sessao);
  const router = inject(Router);
  const tokenEnviado = sessao.tokenAtual();

  return proximo(comToken(requisicao, tokenEnviado)).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse) || erro.status !== 401 || tokenEnviado === null) {
        return throwError(() => erro);
      }
      return sessao.renovar().pipe(
        catchError(() => {
          sessao.encerrar();
          void router.navigate(['/entrar'], { queryParams: { voltar: router.url } });
          return throwError(() => erro);
        }),
        switchMap((novoToken) => proximo(comToken(requisicao, novoToken))),
      );
    }),
  );
};

function comToken<T>(requisicao: HttpRequest<T>, token: string | null): HttpRequest<T> {
  return token
    ? requisicao.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : requisicao;
}
