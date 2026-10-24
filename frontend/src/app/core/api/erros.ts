import { HttpErrorResponse } from '@angular/common/http';

import { Problema } from './modelos';

/** A mensagem que o back-end escreveu para pessoas, ou uma genérica quando ela não existe. */
export function mensagemDeErro(erro: unknown, padrao: string): string {
  if (erro instanceof HttpErrorResponse && erro.status === 0) {
    return 'Sem conexão com o servidor. Confira sua internet e tente de novo.';
  }
  return problemaDe(erro)?.detail ?? padrao;
}

/** Erros de validação por campo (respostas 400 com a lista "erros"). */
export function errosDeCampo(erro: unknown): ReadonlyMap<string, string> {
  return new Map((problemaDe(erro)?.erros ?? []).map((e) => [e.campo, e.mensagem]));
}

function problemaDe(erro: unknown): Problema | null {
  return erro instanceof HttpErrorResponse && typeof erro.error === 'object' && erro.error !== null
    ? (erro.error as Problema)
    : null;
}
