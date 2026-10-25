import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';

import { Notificacao } from '../../core/api/modelos';
import { dataNoFuso } from '../../core/datas';
import {
  Notificacoes,
  descricaoDoSino,
  rotuloDoContador,
} from '../../core/notificacoes/notificacoes';

/** O sino do barbeiro: contador no cabeçalho e, ao abrir, a lista dos avisos mais recentes. */
@Component({
  selector: 'app-sino',
  templateUrl: './sino.html',
  styleUrl: './sino.css',
  host: {
    '(keydown.escape)': 'fechar(true)',
    '(focusout)': 'aoPerderOFoco($event)',
    '(document:click)': 'aoClicar($event)',
  },
})
export class Sino {
  private readonly notificacoes = inject(Notificacoes);
  private readonly router = inject(Router);
  private readonly raiz = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly botao = viewChild.required<ElementRef<HTMLButtonElement>>('botao');

  protected readonly aberto = signal(false);
  protected readonly recentes = this.notificacoes.recentes;
  protected readonly contador = computed(() => rotuloDoContador(this.notificacoes.naoLidas()));
  protected readonly descricao = computed(() => descricaoDoSino(this.notificacoes.naoLidas()));
  protected readonly temNaoLidas = computed(() => this.notificacoes.naoLidas() > 0);

  protected alternar(): void {
    this.aberto.update((aberto) => !aberto);
  }

  protected fechar(devolverFoco = false): void {
    if (!this.aberto()) {
      return;
    }
    this.aberto.set(false);
    if (devolverFoco) {
      this.botao().nativeElement.focus();
    }
  }

  /** Quem sai do sino com o Tab fecha o painel; um clique em área sem foco dentro dele não. */
  protected aoPerderOFoco({ relatedTarget }: FocusEvent): void {
    if (relatedTarget instanceof Node && !this.raiz.nativeElement.contains(relatedTarget)) {
      this.fechar();
    }
  }

  protected aoClicar({ target }: MouseEvent): void {
    if (target instanceof Node && !this.raiz.nativeElement.contains(target)) {
      this.fechar();
    }
  }

  /** Marca como lida e abre a Agenda no dia da reserva. */
  protected async abrir(notificacao: Notificacao): Promise<void> {
    this.fechar();
    if (!notificacao.lida) {
      void this.notificacoes.marcarComoLida(notificacao.id);
    }
    await this.router.navigate(['/agenda'], {
      queryParams: { dia: dataNoFuso(notificacao.inicio) },
    });
  }

  protected marcarTodas(): void {
    void this.notificacoes.marcarTodas();
  }
}
