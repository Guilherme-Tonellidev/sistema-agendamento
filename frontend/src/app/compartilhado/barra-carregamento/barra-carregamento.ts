import { Component, inject } from '@angular/core';

import { Carregamento } from '../../core/carregamento/carregamento';

/** As listras do poste correndo no topo enquanto a API responde. */
@Component({
  selector: 'app-barra-carregamento',
  template: `
    @if (carregamento.ativo()) {
      <div class="barra" role="progressbar" aria-label="Carregando"></div>
    }
  `,
  styles: `
    :host {
      position: fixed;
      inset: 0 0 auto;
      z-index: 1000;
      pointer-events: none;
    }
    .barra {
      height: 6px;
      background: repeating-linear-gradient(
        135deg,
        var(--vermelho-poste) 0 10px,
        var(--creme) 10px 20px
      );
      background-size: 28.28px 100%;
      opacity: 0;
      /* Só aparece se a resposta demorar: respostas rápidas não piscam a barra. */
      animation:
        aparecer 0s 150ms forwards,
        listras 0.5s linear infinite;
    }
    @keyframes aparecer {
      to {
        opacity: 1;
      }
    }
    @keyframes listras {
      to {
        background-position: 28.28px 0;
      }
    }
  `,
})
export class BarraCarregamento {
  protected readonly carregamento = inject(Carregamento);
}
