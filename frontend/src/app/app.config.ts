import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { autenticacaoInterceptor } from './core/autenticacao/autenticacao.interceptor';
import { Sessao } from './core/autenticacao/sessao';
import { carregamentoInterceptor } from './core/carregamento/carregamento.interceptor';

registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Query params e data das rotas chegam como input() nas páginas.
    provideRouter(routes, withComponentInputBinding()),
    // A barra conta a chamada inteira, inclusive a repetição depois de renovar o token.
    provideHttpClient(withInterceptors([carregamentoInterceptor, autenticacaoInterceptor])),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    provideAppInitializer(() => inject(Sessao).restaurar()),
  ],
};
