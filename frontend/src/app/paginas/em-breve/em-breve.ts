import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Cabecalho } from '../../compartilhado/cabecalho/cabecalho';

/** Lugar das telas que entram nas próximas partes da etapa 5. O título vem do data da rota. */
@Component({
  selector: 'app-em-breve',
  imports: [Cabecalho, RouterLink],
  template: `
    <app-cabecalho />
    <main>
      <h1>{{ titulo() }}</h1>
      <p>Esta tela está sendo construída.</p>
      <a routerLink="/horarios">Ver horários livres</a>
    </main>
  `,
  styles: `
    main {
      padding: 56px clamp(16px, 4vw, 56px);
    }
    h1 {
      margin: 0 0 12px;
      font-family: var(--fonte-titulo);
      font-weight: 800;
      font-size: 72px;
      line-height: 0.9;
    }
    p {
      color: var(--texto-suave);
    }
  `,
})
export class EmBreve {
  readonly titulo = input('Em breve');
}
