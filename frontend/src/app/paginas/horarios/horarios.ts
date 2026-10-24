import { CurrencyPipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Observable, firstValueFrom, forkJoin, map } from 'rxjs';

import { mensagemDeErro } from '../../core/api/erros';
import {
  Agendamento,
  HorarioLivre,
  NovoAgendamento,
  Profissional,
  Servico,
} from '../../core/api/modelos';
import { conteudo, ultimoValor } from '../../core/api/recursos';
import { Sessao } from '../../core/autenticacao/sessao';
import { atendeEm } from '../../core/barbearia';
import {
  dataNoFuso,
  dataPorExtenso,
  diaCurto,
  diaDoMes,
  horaNoFuso,
  somarDias,
} from '../../core/datas';
import { Cabecalho } from '../../compartilhado/cabecalho/cabecalho';
import { Ingresso } from '../../compartilhado/ingresso/ingresso';
import { agruparPorPeriodo, primeiroLivrePorHorario } from './grade';

/** Um horário livre e o barbeiro que atende nele. */
interface Opcao extends HorarioLivre {
  readonly hora: string;
  readonly barbeiro: Profissional;
}

interface Consulta {
  readonly servico: Servico;
  readonly barbeiros: readonly Profissional[];
  readonly dia: string;
}

const DIAS_VISIVEIS = 7;

@Component({
  selector: 'app-horarios',
  imports: [CurrencyPipe, RouterLink, Cabecalho, Ingresso],
  templateUrl: './horarios.html',
  styleUrl: './horarios.css',
})
export class Horarios {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly sessao = inject(Sessao);

  /**
   * A escolha fica na URL (?servico=&barbeiro=&dia=&horario=): sobrevive ao login e pode ser
   * compartilhada.
   */
  readonly servico = input<string>();
  readonly barbeiro = input<string>();
  readonly dia = input<string>();
  readonly horario = input<string>();

  private readonly agora = new Date();
  private readonly hoje = dataNoFuso(this.agora);
  protected readonly dias = Array.from({ length: DIAS_VISIVEIS }, (_, i) => {
    const data = somarDias(this.hoje, i);
    return {
      data,
      rotulo: i === 0 ? 'hoje' : diaCurto(data),
      numero: diaDoMes(data),
      fechado: !atendeEm(data, this.agora),
    };
  });

  private readonly catalogo = httpResource(
    () => ({ url: '/api/servicos', params: { ativo: true, size: 50 } }),
    { parse: conteudo<Servico> },
  );
  protected readonly servicos = ultimoValor(this.catalogo, []);
  protected readonly servicoAtual = computed(() => {
    const servicos = this.servicos();
    return servicos.find(({ id }) => id === this.servico()) ?? servicos[0] ?? null;
  });
  protected readonly escolhendoServico = signal(false);

  private readonly equipe = httpResource(
    () => {
      const servico = this.servicoAtual();
      return servico
        ? { url: '/api/profissionais', params: { servicoId: servico.id, ativo: true, size: 50 } }
        : undefined;
    },
    { parse: conteudo<Profissional> },
  );
  protected readonly barbeiros = ultimoValor(this.equipe, []);
  /** null = sem preferência. */
  protected readonly barbeiroAtual = computed(
    () => this.barbeiros().find(({ id }) => id === this.barbeiro()) ?? null,
  );

  protected readonly diaAtual = computed(() => {
    const abertos = this.dias.filter((dia) => !dia.fechado);
    return (abertos.find(({ data }) => data === this.dia()) ?? abertos[0] ?? this.dias[0]).data;
  });

  private readonly livres = rxResource({
    // Espera a equipe do serviço atual: nunca pergunta por um barbeiro que não faz o serviço.
    params: ({ chain }): Consulta | undefined => {
      const equipe = chain(this.equipe) ?? [];
      const servico = this.servicoAtual();
      const escolhido = this.barbeiroAtual();
      const barbeiros = escolhido ? [escolhido] : equipe;
      return servico && barbeiros.length > 0
        ? { servico, barbeiros, dia: this.diaAtual() }
        : undefined;
    },
    stream: ({ params }) => this.buscarOpcoes(params),
  });
  protected readonly opcoes = ultimoValor(this.livres, []);
  protected readonly periodos = computed(() => agruparPorPeriodo(this.opcoes()));
  protected readonly escolhida = computed(
    () => this.opcoes().find(({ inicio }) => inicio === this.horario()) ?? this.opcoes()[0] ?? null,
  );

  /** Na ordem em que dependem uma da outra. */
  private readonly buscas = [this.catalogo, this.equipe, this.livres];
  protected readonly buscando = computed(() => this.buscas.some((busca) => busca.isLoading()));
  protected readonly falhou = computed(() =>
    this.buscas.some((busca) => busca.error() !== undefined),
  );

  protected readonly reservando = signal(false);
  protected readonly erroDaReserva = signal<string | null>(null);
  protected readonly reservado = signal<Agendamento | null>(null);

  /** Visitante pode tentar (vai para o login); barbeiro e administrador não reservam por aqui. */
  protected readonly podeReservar = computed(() => {
    const usuario = this.sessao.usuario();
    return usuario === null || usuario.clienteId !== null;
  });

  /** O ticket mostra a reserva feita ou, antes dela, o horário escolhido. */
  protected readonly bilhete = computed(() => {
    const reservado = this.reservado();
    if (reservado) {
      return this.textos(reservado, reservado.profissional);
    }
    const opcao = this.escolhida();
    return opcao ? this.textos(opcao, opcao.barbeiro) : null;
  });
  protected readonly semPreferencia = computed(
    () => this.barbeiroAtual() === null && this.reservado() === null,
  );

  protected escolherServico(servico: Servico): void {
    this.escolhendoServico.set(false);
    this.mudar({ servico: servico.id, horario: null });
  }

  protected escolherBarbeiro(id: string | null): void {
    this.mudar({ barbeiro: id, horario: null });
  }

  protected escolherDia(data: string): void {
    this.mudar({ dia: data, horario: null });
  }

  protected escolherHorario(opcao: Opcao): void {
    this.mudar({ horario: opcao.inicio });
  }

  /** Repete a primeira busca que falhou; as que dependem dela vêm junto. */
  protected tentarDeNovo(): void {
    this.buscas.find((busca) => busca.error() !== undefined)?.reload();
  }

  protected outroHorario(): void {
    this.reservado.set(null);
  }

  /** Iniciais do primeiro e do último nome: "Rafael Souza" vira "RS". */
  protected sigla(nome: string): string {
    const [primeiro, ...resto] = nome.trim().split(/\s+/);
    return (primeiro.charAt(0) + (resto.at(-1)?.charAt(0) ?? '')).toUpperCase();
  }

  protected async reservar(): Promise<void> {
    const opcao = this.escolhida();
    const servico = this.servicoAtual();
    if (!opcao || !servico) {
      return;
    }
    const usuario = this.sessao.usuario();
    if (!usuario) {
      const voltar = this.router.createUrlTree(['/horarios'], {
        queryParams: {
          servico: servico.id,
          barbeiro: this.barbeiro() ?? null,
          dia: this.diaAtual(),
          horario: opcao.inicio,
        },
      });
      await this.router.navigate(['/entrar'], {
        queryParams: { voltar: this.router.serializeUrl(voltar) },
      });
      return;
    }
    if (!usuario.clienteId) {
      return;
    }
    const pedido: NovoAgendamento = {
      profissionalId: opcao.barbeiro.id,
      servicoId: servico.id,
      clienteId: usuario.clienteId,
      inicio: opcao.inicio,
    };
    this.reservando.set(true);
    this.erroDaReserva.set(null);
    try {
      this.reservado.set(
        await firstValueFrom(this.http.post<Agendamento>('/api/agendamentos', pedido)),
      );
    } catch (erro) {
      this.erroDaReserva.set(
        mensagemDeErro(erro, 'Não foi possível reservar. Escolha outro horário.'),
      );
    } finally {
      this.reservando.set(false);
      // O horário reservado, ou o que alguém pegou antes, sai da grade.
      this.livres.reload();
    }
  }

  /** Muda a escolha pela URL; trocar qualquer coisa desfaz o "Reservado!". */
  private mudar(mudanca: Record<string, string | null>): void {
    this.reservado.set(null);
    this.erroDaReserva.set(null);
    void this.router.navigate(['/horarios'], {
      queryParams: mudanca,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  /** Sem preferência, consulta cada barbeiro e fica com o primeiro livre em cada horário. */
  private buscarOpcoes({ servico, barbeiros, dia }: Consulta): Observable<Opcao[]> {
    const agendas = barbeiros.map((barbeiro) =>
      this.http
        .get<HorarioLivre[]>(`/api/profissionais/${barbeiro.id}/horarios-livres`, {
          params: { servicoId: servico.id, data: dia },
        })
        .pipe(
          map((livres) =>
            livres.map((livre) => ({ ...livre, hora: horaNoFuso(livre.inicio), barbeiro })),
          ),
        ),
    );
    return forkJoin(agendas).pipe(map(primeiroLivrePorHorario));
  }

  private textos({ inicio, fim }: HorarioLivre, barbeiro: Profissional) {
    const dia = dataNoFuso(inicio);
    return {
      hora: horaNoFuso(inicio),
      fim: horaNoFuso(fim),
      data: (dia === this.hoje ? 'hoje, ' : '') + dataPorExtenso(dia),
      barbeiro: barbeiro.nome,
    };
  }
}
