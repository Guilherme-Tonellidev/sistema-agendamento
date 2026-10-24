import { Component, input } from '@angular/core';

/** Metade de uma tesoura apontando para cima: lâmina, haste e anel. */
const PARTES = [
  'M 0 -70 C 8 -48 10 -20 8 4 L -8 4 C -10 -20 -8 -48 0 -70 Z',
  'M -6 0 C -6 14 1 26 5 38 L 17 36 C 12 24 6 12 6 0 Z',
  'M 1 50 a 14 14 0 1 0 28 0 a 14 14 0 1 0 -28 0 Z M 8 50 a 7 7 0 1 0 14 0 a 7 7 0 1 0 -14 0 Z',
];

/** As duas metades de cada tesoura, quase fechadas. */
const METADES = ['scale(-1 1) rotate(-3)', 'rotate(-3)'];

/** Tesouras cruzadas, versão simplificada para tamanhos pequenos (topo das telas e ícone). */
@Component({
  selector: 'app-marca',
  template: `
    <svg
      viewBox="-100 -100 200 200"
      [attr.width]="tamanho()"
      [attr.height]="tamanho()"
      aria-hidden="true"
    >
      <g transform="scale(1.1232) translate(0 0.37)" fill-rule="evenodd">
        <g class="ouro" transform="rotate(-45) translate(0 3)">
          @for (metade of metades; track metade) {
            <g [attr.transform]="metade">
              @for (parte of partes; track parte) {
                <path [attr.d]="parte" />
              }
            </g>
          }
        </g>
        <g transform="rotate(45) translate(0 3)">
          <g class="recorte">
            @for (metade of metades; track metade) {
              <g [attr.transform]="metade">
                @for (parte of partes; track parte) {
                  <path [attr.d]="parte" />
                }
              </g>
            }
          </g>
          <g class="ouro">
            @for (metade of metades; track metade) {
              <g [attr.transform]="metade">
                @for (parte of partes; track parte) {
                  <path [attr.d]="parte" />
                }
              </g>
            }
          </g>
        </g>
      </g>
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
    }
    svg {
      display: block;
    }
    .ouro {
      fill: var(--dourado);
    }
    /* Contorno na cor do fundo: separa a tesoura de cima da de baixo. */
    .recorte {
      fill: var(--fundo-da-marca, var(--carvao));
      stroke: var(--fundo-da-marca, var(--carvao));
      stroke-width: 9;
      stroke-linejoin: round;
    }
  `,
})
export class Marca {
  readonly tamanho = input(34);

  protected readonly partes = PARTES;
  protected readonly metades = METADES;
}
