import { HttpClient, httpResource } from '@angular/common/http';
import { Component, computed, inject, resource, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { mensagemDeErro } from '../../core/api/erros';
import { Agendamento, Pagina, Profissional } from '../../core/api/modelos';
import { conteudo, ultimoValor } from '../../core/api/recursos';
import { dataNoFuso } from '../../core/datas';
import { Cabecalho } from '../../compartilhado/cabecalho/cabecalho';
import { Calendario } from './calendario';
import { corDoBarbeiro, paraEvento, semanaDe, todasAsPaginas } from './eventos';

type Painel =
  | null
  | { readonly tipo: 'marcar'; readonly inicio: Date }
  | { readonly tipo: 'detalhes'; readonly agendamento: Agendamento };

@Component({
  selector: 'app-semana',
  imports: [Cabecalho, Calendario],
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
    () => ({ url: '/api/profissionais', params: { ativo: true, size: 50 } }),
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

  protected readonly legenda = computed(() => {
    const equipe = this.barbeiros();
    return equipe.map(({ id, nome }) => ({
      id,
      nome,
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

  private readonly buscas = [this.equipe, this.lista];
  protected readonly erro = computed(() => {
    const erro = this.buscas.find((busca) => busca.error() !== undefined)?.error();
    // O resource embrulha o que não é Error (como o HttpErrorResponse) no cause.
    return erro ? mensagemDeErro(erro.cause ?? erro, 'Não foi possível carregar a semana.') : null;
  });

  protected irPara(dia: string): void {
    this.semana.set(semanaDe(dia));
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

  /** Repete as buscas que falharam. */
  protected tentarDeNovo(): void {
    this.buscas.filter((busca) => busca.error() !== undefined).forEach((busca) => busca.reload());
  }
}
