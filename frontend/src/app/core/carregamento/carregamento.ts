import { Injectable, computed, signal } from '@angular/core';

/** Quantas chamadas à API estão em andamento; alimenta a barra listrada do topo. */
@Injectable({ providedIn: 'root' })
export class Carregamento {
  private readonly pendentes = signal(0);

  readonly ativo = computed(() => this.pendentes() > 0);

  comecou(): void {
    this.pendentes.update((quantidade) => quantidade + 1);
  }

  terminou(): void {
    this.pendentes.update((quantidade) => Math.max(0, quantidade - 1));
  }
}
