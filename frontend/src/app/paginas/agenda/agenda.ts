import { HttpClient, httpResource } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { mensagemDeErro } from '../../core/api/erros';
import {
  Agendamento,
  Bloqueio,
  HorarioLivre,
  Profissional,
  ProfissionalComServicos,
  ServicoResumo,
} from '../../core/api/modelos';
import { conteudo, ultimoValor } from '../../core/api/recursos';
import { Sessao } from '../../core/autenticacao/sessao';
import {
  dataNoFuso,
  diaCurto,
  diaDaSemanaPorExtenso,
  diaDoMes,
  horaNoFuso,
  somarDias,
} from '../../core/datas';
import { Cabecalho } from '../../compartilhado/cabecalho/cabecalho';
import { PrecoPipe, duracao } from '../../compartilhado/formatos';
import { Ingresso } from '../../compartilhado/ingresso/ingresso';
import { Estado, Vao, estadoDe, juntarEmVaos, montarFila, resumirDia } from './fila';
import { PainelAtendimento } from './painel-atendimento';
import { PainelBloqueio } from './painel-bloqueio';
import { PainelEncaixe } from './painel-encaixe';

type Painel = 'atendimento' | 'encaixe' | 'bloqueio';

type Linha =
  | {
      readonly tipo: 'atendimento';
      readonly chave: string;
      readonly hora: string;
      readonly cliente: string;
      readonly detalhe: string;
      readonly estado: Estado;
      readonly encerrado: boolean;
      readonly marcado: boolean;
      readonly agendamento: Agendamento;
    }
  | {
      readonly tipo: 'vao';
      readonly chave: string;
      readonly hora: string;
      readonly texto: string;
      readonly chamada: string;
      readonly encaixando: boolean;
      readonly inicio: string;
    }
  | {
      readonly tipo: 'bloqueio';
      readonly chave: string;
      readonly hora: string;
      readonly texto: string;
      readonly id: string;
    };

const SELO: Record<Estado, string> = {
  confirmado: 'Confirmado',
  andamento: 'Em andamento',
  pendente: 'Pendente',
  concluido: 'Concluído',
  faltou: 'Não veio',
  cancelado: 'Cancelado',
};

/** Ontem, para dar baixa no que ficou aberto, hoje e os próximos dias. */
const DIAS_ANTES = 1;
const DIAS_DEPOIS = 5;
/** A fila se atualiza sozinha: o tempo passa e clientes reservam pelo site. */
const ATUALIZAR_A_CADA_MS = 60_000;
const DIA_VALIDO = /^\d{4}-\d{2}-\d{2}$/;

@Component({
  selector: 'app-agenda',
  imports: [Cabecalho, Ingresso, PainelAtendimento, PainelBloqueio, PainelEncaixe, PrecoPipe],
  templateUrl: './agenda.html',
  styleUrl: './agenda.css',
})
export class Agenda {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly sessao = inject(Sessao);

  /** O dia (AAAA-MM-DD) e, para o administrador, o barbeiro ficam na URL. */
  readonly dia = input<string>();
  readonly barbeiro = input<string>();

  protected readonly agora = signal(new Date());
  private readonly hoje = computed(() => dataNoFuso(this.agora()));
  protected readonly diaAtual = computed(() => {
    const dia = this.dia();
    return dia && DIA_VALIDO.test(dia) ? dia : this.hoje();
  });
  protected readonly ehHoje = computed(() => this.diaAtual() === this.hoje());
  /** Encaixe e bloqueio só de hoje em diante. */
  protected readonly podeMarcar = computed(() => this.diaAtual() >= this.hoje());
  protected readonly dias = computed(() =>
    Array.from({ length: DIAS_ANTES + 1 + DIAS_DEPOIS }, (_, i) => {
      const data = somarDias(this.hoje(), i - DIAS_ANTES);
      return {
        data,
        rotulo: data === this.hoje() ? 'hoje' : diaCurto(data),
        numero: diaDoMes(data),
      };
    }),
  );

  /** O barbeiro vê a própria agenda; o administrador escolhe de quem. */
  private readonly equipe = httpResource(
    () =>
      this.sessao.usuario()?.papel === 'ADMIN'
        ? { url: '/api/profissionais', params: { ativo: true, size: 50 } }
        : undefined,
    { parse: conteudo<Profissional> },
  );
  protected readonly barbeiros = ultimoValor(this.equipe, []);
  protected readonly profissionalId = computed(() => {
    const barbeiros = this.barbeiros();
    return (
      this.sessao.usuario()?.profissionalId ??
      (barbeiros.find(({ id }) => id === this.barbeiro()) ?? barbeiros[0])?.id ??
      null
    );
  });

  private readonly profissional = httpResource<ProfissionalComServicos>(() => {
    const id = this.profissionalId();
    return id ? `/api/profissionais/${id}` : undefined;
  });
  /** Em ordem alfabética, como na tabela de preços: a API não garante ordem. */
  protected readonly servicos = computed(() =>
    this.profissional.hasValue()
      ? [...this.profissional.value().servicos].sort((a, b) =>
          a.nome.localeCompare(b.nome, 'pt-BR'),
        )
      : [],
  );

  private readonly lista = httpResource(
    () => {
      const id = this.profissionalId();
      const dia = this.diaAtual();
      return id
        ? {
            url: '/api/agendamentos',
            params: { profissionalId: id, de: dia, ate: dia, size: 100, sort: 'inicio' },
          }
        : undefined;
    },
    { parse: conteudo<Agendamento> },
  );
  private readonly bloqueiosDoDia = httpResource<Bloqueio[]>(() => {
    const id = this.profissionalId();
    const dia = this.diaAtual();
    return id
      ? { url: `/api/profissionais/${id}/bloqueios`, params: { de: dia, ate: dia } }
      : undefined;
  });
  /** Os horários livres do serviço mais curto viram os vãos da fila; dias passados não têm. */
  private readonly livres = httpResource<HorarioLivre[]>(() => {
    const id = this.profissionalId();
    const servico = this.servicos().reduce<ServicoResumo | null>(
      (menor, atual) => (!menor || atual.duracaoMinutos < menor.duracaoMinutos ? atual : menor),
      null,
    );
    return id && servico && this.podeMarcar()
      ? {
          url: `/api/profissionais/${id}/horarios-livres/encaixe`,
          params: { servicoId: servico.id, data: this.diaAtual() },
        }
      : undefined;
  });

  private readonly agendamentos = ultimoValor(this.lista, []);
  private readonly bloqueios = ultimoValor(this.bloqueiosDoDia, []);
  private readonly horariosLivres = ultimoValor(this.livres, []);
  private readonly vaos = computed(() => juntarEmVaos(this.horariosLivres()));
  protected readonly fila = computed(() =>
    montarFila(this.agendamentos(), this.bloqueios(), this.vaos(), this.agora()),
  );
  protected readonly resumo = computed(() => resumirDia(this.agendamentos()));

  private readonly buscas = [this.profissional, this.lista, this.bloqueiosDoDia, this.livres];
  protected readonly carregando = computed(() => this.lista.isLoading() && !this.lista.hasValue());
  protected readonly falhou = computed(() =>
    this.buscas.some((busca) => busca.error() !== undefined),
  );
  protected readonly aviso = signal<string | null>(null);

  protected readonly selo = SELO;
  protected readonly painel = signal<Painel>('atendimento');
  private readonly escolhido = signal<string | null>(null);
  /** Do vão clicado; o painel de encaixe devolve o horário que de fato vai usar. */
  protected readonly inicioDoEncaixe = signal<string | null>(null);
  protected readonly horarioDoEncaixe = signal<string | null>(null);

  /** No ticket: o atendimento escolhido ou, sem escolha, o de agora ou o próximo. */
  protected readonly atual = computed(() => {
    const { agora, proximos, encerrados } = this.fila();
    return (
      this.agendamentos().find(({ id }) => id === this.escolhido()) ??
      agora[0] ??
      proximos.flatMap((item) => (item.tipo === 'atendimento' ? [item.agendamento] : []))[0] ??
      encerrados.at(-1) ??
      null
    );
  });

  protected readonly secoes = computed(() => {
    const { agora, proximos, encerrados } = this.fila();
    const hoje = this.ehHoje();
    return [
      {
        id: 'agora',
        titulo: hoje ? 'Agora' : 'Sem baixa',
        linhas: agora.map((a) => this.linha(a)),
      },
      {
        id: 'proximos',
        titulo: 'Próximos',
        linhas: proximos.map((item) =>
          item.tipo === 'atendimento'
            ? this.linha(item.agendamento)
            : item.tipo === 'vao'
              ? this.linhaDoVao(item.vao)
              : this.linhaDoBloqueio(item.bloqueio),
        ),
      },
      {
        id: 'encerrados',
        titulo: hoje ? 'Encerrados hoje' : 'Encerrados',
        linhas: encerrados.map((a) => this.linha(a)),
      },
    ].filter(({ linhas }) => linhas.length > 0);
  });

  /** Hoje, a hora; em outro dia, qual é ele. */
  protected readonly quando = computed(() =>
    this.ehHoje()
      ? { rotulo: 'agora', valor: horaNoFuso(this.agora()) }
      : {
          rotulo: diaDaSemanaPorExtenso(this.diaAtual()),
          valor: `${diaDoMes(this.diaAtual())}/${this.diaAtual().slice(5, 7)}`,
        },
  );

  private readonly lado = viewChild.required<ElementRef<HTMLElement>>('lado');

  constructor() {
    const relogio = setInterval(() => {
      this.agora.set(new Date());
      this.recarregar();
    }, ATUALIZAR_A_CADA_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(relogio));
  }

  protected escolherDia(data: string): void {
    this.navegar({ dia: data === this.hoje() ? null : data });
  }

  protected escolherBarbeiro(id: string): void {
    this.navegar({ barbeiro: id });
  }

  protected selecionar({ id }: Agendamento): void {
    this.escolhido.set(id);
    this.abrir('atendimento');
  }

  protected encaixar(inicio: string | null): void {
    this.inicioDoEncaixe.set(inicio);
    this.abrir('encaixe');
  }

  /** Os botões do topo abrem o painel ou, se ele já estiver aberto, voltam ao atendimento. */
  protected alternar(painel: 'encaixe' | 'bloqueio'): void {
    if (this.painel() === painel) {
      this.painel.set('atendimento');
    } else if (painel === 'encaixe') {
      this.encaixar(null);
    } else {
      this.abrir(painel);
    }
  }

  protected encaixado(agendamento: Agendamento): void {
    this.selecionar(agendamento);
    this.recarregar();
  }

  protected bloqueado(): void {
    this.painel.set('atendimento');
    this.recarregar();
  }

  protected async desbloquear(id: string): Promise<void> {
    this.aviso.set(null);
    try {
      await firstValueFrom(
        this.http.delete(`/api/profissionais/${this.profissionalId()}/bloqueios/${id}`),
      );
    } catch (erro) {
      this.aviso.set(mensagemDeErro(erro, 'Não foi possível desbloquear agora. Tente de novo.'));
    }
    this.recarregar();
  }

  protected recarregar(): void {
    this.lista.reload();
    this.bloqueiosDoDia.reload();
    this.livres.reload();
  }

  /** Repete as buscas que falharam. */
  protected tentarDeNovo(): void {
    this.buscas.filter((busca) => busca.error() !== undefined).forEach((busca) => busca.reload());
  }

  private abrir(painel: Painel): void {
    this.painel.set(painel);
    // No celular o ticket fica acima da fila: rola até ele.
    if (matchMedia('(max-width: 960px)').matches) {
      this.lado().nativeElement.scrollIntoView({ block: 'start' });
    }
  }

  private navegar(mudanca: Record<string, string | null>): void {
    this.painel.set('atendimento');
    this.escolhido.set(null);
    this.aviso.set(null);
    void this.router.navigate(['/agenda'], {
      queryParams: mudanca,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private linha(agendamento: Agendamento): Linha {
    const estado = estadoDe(agendamento, this.agora());
    return {
      tipo: 'atendimento',
      chave: agendamento.id,
      hora: horaNoFuso(agendamento.inicio),
      cliente: agendamento.cliente.nome,
      detalhe: `${agendamento.servico.nome.toLowerCase()} · até ${horaNoFuso(agendamento.fim)}`,
      estado,
      encerrado: estado === 'concluido' || estado === 'faltou',
      marcado: this.painel() === 'atendimento' && agendamento.id === this.atual()?.id,
      agendamento,
    };
  }

  private linhaDoVao({ inicio, fim }: Vao): Linha {
    const horario = this.painel() === 'encaixe' ? this.horarioDoEncaixe() : null;
    const noVao =
      horario && Date.parse(horario) >= Date.parse(inicio) && Date.parse(horario) < Date.parse(fim);
    const minutos = (Date.parse(fim) - Date.parse(inicio)) / 60_000;
    return {
      tipo: 'vao',
      chave: `vao-${inicio}`,
      hora: horaNoFuso(inicio),
      texto: noVao
        ? `Encaixe às ${horaNoFuso(horario)}`
        : `Livre até ${horaNoFuso(fim)} · ${duracao(minutos)}`,
      chamada: noVao ? 'Encaixando' : '+ Encaixar',
      encaixando: Boolean(noVao),
      inicio,
    };
  }

  /** Bloqueios de vários dias aparecem do começo ao fim de cada um. */
  private linhaDoBloqueio({ id, inicio, fim, motivo }: Bloqueio): Linha {
    const dia = this.diaAtual();
    const ate = dataNoFuso(fim) === dia ? `até ${horaNoFuso(fim)}` : 'até o fim do dia';
    return {
      tipo: 'bloqueio',
      chave: `bloqueio-${id}`,
      hora: dataNoFuso(inicio) === dia ? horaNoFuso(inicio) : '00:00',
      texto: [`Bloqueado ${ate}`, motivo].filter(Boolean).join(' · '),
      id,
    };
  }
}
