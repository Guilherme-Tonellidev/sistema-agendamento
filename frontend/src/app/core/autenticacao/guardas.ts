import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Papel, Usuario } from '../api/modelos';
import { Sessao } from './sessao';

/** Página inicial de cada papel. */
export function inicioDe(usuario: Usuario): string {
  return usuario.papel === 'CLIENTE' ? '/horarios' : '/agenda';
}

/** Caminho desta aplicação: "/algo", mas não "//site" nem "/\site", que levam a outro site. */
const CAMINHO_INTERNO = /^\/(?![/\\])/;

/** Só volta para caminhos internos: um link malicioso não usa o login como redirecionador. */
export function destinoAposEntrar(usuario: Usuario, voltar: string | undefined): string {
  return voltar && CAMINHO_INTERNO.test(voltar) ? voltar : inicioDe(usuario);
}

export function exigePapel(...papeis: Papel[]): CanActivateFn {
  return (_rota, estado) => {
    const usuario = inject(Sessao).usuario();
    const router = inject(Router);
    if (!usuario) {
      return router.createUrlTree(['/entrar'], { queryParams: { voltar: estado.url } });
    }
    return papeis.includes(usuario.papel) || router.createUrlTree([inicioDe(usuario)]);
  };
}

/** Telas de entrar e criar conta não fazem sentido para quem já está logado. */
export const somenteVisitante: CanActivateFn = () => {
  const usuario = inject(Sessao).usuario();
  return usuario ? inject(Router).createUrlTree([inicioDe(usuario)]) : true;
};
