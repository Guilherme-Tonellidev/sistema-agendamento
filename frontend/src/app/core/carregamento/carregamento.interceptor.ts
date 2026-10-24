import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';

import { Carregamento } from './carregamento';

export const carregamentoInterceptor: HttpInterceptorFn = (requisicao, proximo) => {
  const carregamento = inject(Carregamento);
  carregamento.comecou();
  return proximo(requisicao).pipe(finalize(() => carregamento.terminou()));
};
