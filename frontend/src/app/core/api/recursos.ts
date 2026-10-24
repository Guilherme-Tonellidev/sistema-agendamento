import { Resource, ResourceSnapshot, Signal, linkedSignal } from '@angular/core';

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

/** Para o parse do httpResource: das listas paginadas do Spring, as telas só usam o conteúdo. */
export function conteudo<T>(pagina: unknown): readonly T[] {
  return (pagina as Pagina<T>).content;
}
