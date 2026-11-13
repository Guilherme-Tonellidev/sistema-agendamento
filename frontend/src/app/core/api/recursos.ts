import { Resource, ResourceSnapshot, Signal, linkedSignal } from '@angular/core';

import { mensagemDeErro } from './erros';
import { Pagina } from './modelos';

/**
 * O valor do recurso, segurando o último resultado enquanto o próximo carrega: trocar de dia
 * ou de serviço esmaece a tela em vez de esvaziá-la. Sem resultado (antes da primeira
 * resposta, sem busca ou com erro), vale o inicial.
 */
export function ultimoValor<T>(recurso: Resource<T | undefined>, inicial: NoInfer<T>): Signal<T> {
  return linkedSignal<ResourceSnapshot<T | undefined>, T>({
    source: recurso.snapshot,
    computation: (agora, antes) => {
      switch (agora.status) {
        case 'resolved':
        case 'local':
          return agora.value ?? inicial;
        case 'loading':
        case 'reloading':
          return antes?.value ?? inicial;
        default:
          return inicial;
      }
    },
  }).asReadonly();
}

/**
 * A mensagem de uma busca que falhou, ou null. O resource embrulha o que não é Error (como o
 * HttpErrorResponse) no cause.
 */
export function falhaDe(busca: Resource<unknown>, padrao: string): string | null {
  const erro = busca.error();
  return erro ? mensagemDeErro(erro.cause ?? erro, padrao) : null;
}

/** Para o parse do httpResource: das listas paginadas do Spring, as telas só usam o conteúdo. */
export function conteudo<T>(pagina: unknown): readonly T[] {
  return (pagina as Pagina<T>).content;
}
