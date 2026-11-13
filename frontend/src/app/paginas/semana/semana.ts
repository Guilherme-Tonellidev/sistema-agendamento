import { HttpClient, httpResource } from '@angular/common/http';
import { Component, computed, inject, resource, signal, viewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { mensagemDeErro } from '../../core/api/erros';
import { Agendamento, Pagina, Profissional } from '../../core/api/modelos';
import { conteudo, falhaDe, ultimoValor } from '../../core/api/recursos';
import { dataNoFuso } from '../../core/datas';
import { Cabecalho } from '../../compartilhado/cabecalho/cabecalho';
import { BlocoMovido, Calendario } from './calendario';
import { FormularioMarcar } from './formulario-marcar';
import { PainelDetalhes } from './painel-detalhes';
import { corDoBarbeiro, paraEvento, semanaDe, todasAsPaginas } from './eventos';

type Painel =
  | null
  | { readonly tipo: 'marcar'; readonly inicio: Date }
  | { readonly tipo: 'detalhes'; readonly agendamento: Agendamento };

@Component({
  selector: 'app-semana',
  imports: [Cabecalho, Calendario, FormularioMarcar, PainelDetalhes],
  templateUrl: './semana.html',
  styleUrl: './semana.css',
})
export class Semana {
  private readonly http = inject(HttpClient);

  /** Segunda a domingo. A mesma segunda não conta como mudança, então não busca de novo. */
  readonly semana = signal(semanaDe(dataNoFuso(new Date())), {
    equal: (a, b) => a.de === b.de,
  });

  private readonly equipe = httpResource(
    // Inativos também: os agendamentos antigos deles seguem na grade, com cor e legenda.
    () => ({ url: '/api/profissionais', params: { size: 50 } }),
    { parse: conteudo<Profissional> },
  );
  protected readonly barbeiros = ultimoValor(this.equipe, []);

  /** A API filtra um status só: vem a semana inteira e os cancelados saem aqui. */
  private readonly lista = resource({
    params: () => this.semana(),
    loader: ({ params: { de, ate } }) =>
      todasAsPaginas((page) =>
        firstValueFrom(
          this.http.get<Pagina<Agendamento>>('/api/agendamentos', {
            params: { de, ate, page, size: 100, sort: 'inicio' },
          }),
        ),
      ),
  });
  readonly agendamentos = ultimoValor(this.lista, []);
  protected readonly carregando = computed(() => this.lista.isLoading());

  protected readonly ocultos = signal<ReadonlySet<string>>(new Set());
  protected readonly mostrarCancelados = signal(false);

  private readonly naSemana = computed(
    () => new Set(this.agendamentos().map(({ profissional }) => profissional.id)),
  );

  /** A cor sai da equipe inteira, igual em toda semana; inativo só entra se atendeu nesta. */
  protected readonly legenda = computed(() => {
    const equipe = this.barbeiros();
    const naSemana = this.naSemana();
    return equipe
      .filter(({ id, ativo }) => ativo || naSemana.has(id))
      .map(({ id, nome, ativo }) => ({
        id,
        nome,
        ativo,
        cor: corDoBarbeiro(equipe, id),
        ligado: !this.ocultos().has(id),
      }));
  });

  protected readonly eventos = computed(() => {
    const equipe = this.barbeiros();
    const ocultos = this.ocultos();
    const cancelados = this.mostrarCancelados();
    return this.agendamentos()
      .filter(
        ({ status, profissional }) =>
          (cancelados || status !== 'CANCELADO') && !ocultos.has(profissional.id),
      )
      .map((agendamento) => paraEvento(agendamento, equipe));
  });

  readonly painel = signal<Painel>(null);
  /** Em lista para o @for recriar o painel a cada bloco escolhido: nada de um vaza para o outro. */
  protected readonly detalhes = computed(() => {
    const aberto = this.painel();
    return aberto?.tipo === 'detalhes' ? [aberto.agendamento] : [];
  });

  private readonly buscas = [this.equipe, this.lista];
  protected readonly erro = computed(() => {
    const falhou = this.buscas.find((busca) => busca.error() !== undefined);
    return falhou ? falhaDe(falhou, 'Não foi possível carregar a semana.') : null;
  });

  /** A recusa do último arraste, até o próximo que der certo, a troca de semana ou o Fechar. */
  protected readonly avisoDoArraste = signal<string | null>(null);
  /** Blocos com a remarcação a caminho: soltar o mesmo bloco de novo não abre outro pedido. */
  private readonly remarcandoAgora = new Set<string>();
  private readonly detalhesAbertos = viewChild(PainelDetalhes);

  /** Trocar de semana tira o aviso do arraste: ele falava de um bloco da semana anterior. */
  protected irPara(dia: string): void {
    this.semana.set(semanaDe(dia));
    this.avisoDoArraste.set(null);
  }

  protected alternar(id: string): void {
    this.ocultos.update((ocultos) => {
      const novos = new Set(ocultos);
      if (!novos.delete(id)) {
        novos.add(id);
      }
      return novos;
    });
  }

  /** O bloco novo aparece na grade com a semana buscada de novo. */
  protected marcou(): void {
    this.painel.set(null);
    this.lista.reload();
  }

  /**
   * Remarcou, cancelou, concluiu ou marcou falta: o painel segue aberto com o agendamento como o
   * servidor devolveu (mesmo que ele saia da grade) e a semana é buscada de novo.
   */
  protected atualizou(agendamento: Agendamento): void {
    this.painel.set({ tipo: 'detalhes', agendamento });
    this.lista.reload();
  }

  /** Bloco solto em outro horário: o servidor confirma, ou o bloco volta e o motivo vai ao topo. */
  protected async remarcarPorArraste({
    agendamento,
    novoInicio,
    reverter,
  }: BlocoMovido): Promise<void> {
    const { id } = agendamento;
    if (this.remarcandoAgora.has(id)) {
      reverter();
      return;
    }
    this.remarcandoAgora.add(id);
    let remarcado: Agendamento;
    try {
      remarcado = await firstValueFrom(
        this.http.patch<Agendamento>(`/api/agendamentos/${id}/reagendar`, { inicio: novoInicio }),
      );
    } catch (erro) {
      reverter();
      this.avisoDoArraste.set(mensagemDeErro(erro, 'Não foi possível remarcar.'));
      return;
    } finally {
      this.remarcandoAgora.delete(id);
    }
    this.avisoDoArraste.set(null);
    // Com os detalhes deste bloco abertos, o painel segue o mesmo caminho do botão Remarcar:
    // mostra o novo horário, recarrega o histórico e pede a semana de novo.
    const detalhes = this.detalhesAbertos();
    if (detalhes?.agendamento().id === id) {
      detalhes.atualizou(remarcado);
    } else {
      this.lista.reload();
    }
  }

  /** Repete as buscas que falharam. */
  protected tentarDeNovo(): void {
    this.buscas.filter((busca) => busca.error() !== undefined).forEach((busca) => busca.reload());
  }
}
