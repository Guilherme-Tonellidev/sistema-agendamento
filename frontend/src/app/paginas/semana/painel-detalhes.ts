import { HttpClient, httpResource } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  Resource,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { mensagemDeErro } from '../../core/api/erros';
import { Agendamento, HorarioLivre } from '../../core/api/modelos';
import { ultimoValor } from '../../core/api/recursos';
import { comMaiuscula, dataNoFuso, dataPorExtenso, horaNoFuso } from '../../core/datas';
import { PainelAtendimento } from '../agenda/painel-atendimento';
import { LinhaDoTempo } from './linha-do-tempo';

/** Em andamento vira pendente com o tempo, como na agenda. */
const ATUALIZAR_A_CADA_MS = 60_000;

/** Cópia do helper do FormularioMarcar; vira compartilhado quando houver um terceiro uso. */
function falhaDe(busca: Resource<unknown>, padrao: string): string | null {
  const erro = busca.error();
  return erro ? mensagemDeErro(erro.cause ?? erro, padrao) : null;
}

/** O bloco clicado na semana: o atendimento com as ações do admin, remarcar e o histórico. */
@Component({
  selector: 'app-painel-detalhes',
  imports: [LinhaDoTempo, PainelAtendimento],
  templateUrl: './painel-detalhes.html',
  styleUrl: '../agenda/painel.css',
  // Sem :host aqui: o painel.css já define o dele, e a ordem entre os dois não importa assim.
  styles: `
    .detalhes {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .cabeca {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: 12px;
      color: var(--texto-claro);
    }
    .cabeca p {
      margin: 0;
    }
    .extra {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 20px 30px;
      border-radius: 22px;
      background: var(--creme);
      color: var(--tinta);
    }
    select {
      min-height: 44px;
      border: 2px solid var(--tinta);
      border-radius: 12px;
      background: var(--creme-claro);
      padding: 0 10px;
      color: var(--tinta);
    }
  `,
})
export class PainelDetalhes {
  private readonly http = inject(HttpClient);

  readonly agendamento = input.required<Agendamento>();
  /** Remarcou, cancelou, concluiu ou marcou falta: a semana recarrega e fecha o painel. */
  readonly mudou = output<void>();
  readonly fechar = output<void>();

  protected readonly agora = signal(new Date());

  protected readonly resumo = computed(() => {
    const { inicio, profissional } = this.agendamento();
    return {
      dia: comMaiuscula(dataPorExtenso(dataNoFuso(inicio))),
      barbeiro: profissional.nome,
    };
  });

  protected readonly remarcando = signal(false);
  protected readonly dia = linkedSignal(() => dataNoFuso(this.agendamento().inicio));
  /** Sem limite: o administrador remarca para o passado e além da janela de reserva. */
  private readonly livres = httpResource<HorarioLivre[]>(() => {
    if (!this.remarcando()) {
      return undefined;
    }
    const { profissional, servico } = this.agendamento();
    return {
      url: `/api/profissionais/${profissional.id}/horarios-livres/encaixe`,
      params: { servicoId: servico.id, data: this.dia(), semLimite: true },
    };
  });
  private readonly lista = ultimoValor(this.livres, []);
  protected readonly horarios = computed(() =>
    this.lista().map(({ inicio }) => ({ inicio, hora: horaNoFuso(inicio) })),
  );
  protected readonly buscando = this.livres.isLoading;
  protected readonly falhaNosHorarios = computed(() =>
    falhaDe(this.livres, 'Não foi possível carregar os horários.'),
  );
  /** Volta ao primeiro livre sempre que a lista muda. */
  protected readonly escolhido = linkedSignal(() => this.lista()[0]?.inicio ?? null);

  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);

  constructor() {
    const relogio = setInterval(() => this.agora.set(new Date()), ATUALIZAR_A_CADA_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(relogio));
  }

  /** Campo de data apagado não busca nada: fica o último dia válido. */
  protected escolherDia(dia: string): void {
    if (dia) {
      this.dia.set(dia);
    }
  }

  protected async remarcar(): Promise<void> {
    const inicio = this.escolhido();
    if (!inicio) {
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    try {
      await firstValueFrom(
        this.http.patch(`/api/agendamentos/${this.agendamento().id}/reagendar`, { inicio }),
      );
      this.mudou.emit();
    } catch (erro) {
      this.erro.set(mensagemDeErro(erro, 'Não foi possível remarcar agora. Tente de novo.'));
      // Se alguém pegou o horário antes, a lista se atualiza.
      this.livres.reload();
    } finally {
      this.enviando.set(false);
    }
  }
}
