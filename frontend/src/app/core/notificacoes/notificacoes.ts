import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Notificacao, NotificacoesResposta } from '../api/modelos';
import { Sessao } from '../autenticacao/sessao';

const CONSULTAR_A_CADA_MS = 30_000;
const NENHUMA: NotificacoesResposta = { naoLidas: 0, recentes: [] };

/** O que não estava na consulta anterior. Na primeira consulta nada é novo: a pessoa acabou de abrir o site. */
export function novasNotificacoes(
  antes: readonly Notificacao[] | undefined,
  agora: readonly Notificacao[],
): readonly Notificacao[] {
  if (!antes) {
    return [];
  }
  const conhecidas = new Set(antes.map(({ id }) => id));
  return agora.filter(({ id }) => !conhecidas.has(id));
}

/** O número sobre o sino: some quando não há o que ler e passa de 9 para "9+". */
export function rotuloDoContador(naoLidas: number): string {
  return naoLidas === 0 ? '' : naoLidas > 9 ? '9+' : String(naoLidas);
}

/** O nome do sino para leitores de tela, com a contagem exata. */
export function descricaoDoSino(naoLidas: number): string {
  return naoLidas === 0
    ? 'Notificações'
    : `Notificações, ${naoLidas} ${naoLidas === 1 ? 'não lida' : 'não lidas'}`;
}

/**
 * Os avisos do barbeiro. Consulta a cada 30 s com a aba à vista e ao voltar para ela, só enquanto há um
 * barbeiro logado. Uma notificação nova sobe {@link chegouNova}, para as telas que mostram o mesmo dado
 * (a Agenda) atualizarem na hora.
 */
@Injectable({ providedIn: 'root' })
export class Notificacoes {
  private readonly http = inject(HttpClient);
  private readonly sessao = inject(Sessao);

  private readonly resposta = signal(NENHUMA);
  private readonly novas = signal(0);
  private anteriores: readonly Notificacao[] | undefined;
  /** Respostas atrasadas (de antes de um clique, de um logout) não sobrescrevem as mais novas. */
  private ultimaConsulta = 0;

  readonly naoLidas = computed(() => this.resposta().naoLidas);
  readonly recentes = computed(() => this.resposta().recentes);
  /** Quantas notificações novas chegaram desde que o site abriu. */
  readonly chegouNova = this.novas.asReadonly();

  constructor() {
    const ehBarbeiro = computed(() => this.sessao.usuario()?.papel === 'PROFISSIONAL');
    effect((aoLimpar) => {
      if (!ehBarbeiro()) {
        return;
      }
      const consultarSeVisivel = () => !document.hidden && void this.consultar();
      untracked(() => void this.consultar());
      const relogio = setInterval(consultarSeVisivel, CONSULTAR_A_CADA_MS);
      document.addEventListener('visibilitychange', consultarSeVisivel);
      aoLimpar(() => {
        clearInterval(relogio);
        document.removeEventListener('visibilitychange', consultarSeVisivel);
        this.ultimaConsulta++;
        this.anteriores = undefined;
        this.resposta.set(NENHUMA);
      });
    });
  }

  marcarComoLida(id: string): Promise<void> {
    return this.alterar(`/api/notificacoes/${id}/ler`);
  }

  marcarTodas(): Promise<void> {
    return this.alterar('/api/notificacoes/ler-todas');
  }

  private async alterar(url: string): Promise<void> {
    try {
      await firstValueFrom(this.http.patch<void>(url, null));
    } catch {
      // Falhou: a consulta abaixo mostra o que de fato ficou gravado.
    }
    await this.consultar();
  }

  private async consultar(): Promise<void> {
    const minha = ++this.ultimaConsulta;
    try {
      const resposta = await firstValueFrom(
        this.http.get<NotificacoesResposta>('/api/notificacoes'),
      );
      if (minha !== this.ultimaConsulta) {
        return;
      }
      const chegaram = novasNotificacoes(this.anteriores, resposta.recentes);
      this.anteriores = resposta.recentes;
      this.resposta.set(resposta);
      if (chegaram.length > 0) {
        this.novas.update((total) => total + chegaram.length);
      }
    } catch {
      // Sem rede ou sessão vencida: o sino fica como estava e tenta de novo na próxima consulta.
    }
  }
}
