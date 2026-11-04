import { httpResource } from '@angular/common/http';
import { Component, Resource, computed, input } from '@angular/core';

import { mensagemDeErro } from '../../core/api/erros';
import { EventoDoHistorico } from '../../core/api/modelos';
import { diaEHora } from '../../core/datas';
import { fraseDoEvento } from './frase-do-evento';

export { fraseDoEvento } from './frase-do-evento';

/** Cópia do helper do FormularioMarcar; vira compartilhado quando houver um terceiro uso. */
function falhaDe(busca: Resource<unknown>, padrao: string): string | null {
  const erro = busca.error();
  return erro ? mensagemDeErro(erro.cause ?? erro, padrao) : null;
}

/** Quem criou, remarcou, cancelou, concluiu ou marcou falta, e quando. */
@Component({
  selector: 'app-linha-do-tempo',
  templateUrl: './linha-do-tempo.html',
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    h3 {
      margin: 0;
      font-size: 14px;
      font-weight: 700;
    }
    ol {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 6px;
      font-size: 14px;
    }
    time,
    .nota {
      color: var(--texto-escuro-suave);
    }
    time {
      margin-right: 8px;
      font-variant-numeric: tabular-nums;
    }
    .nota {
      margin: 0;
      font-size: 14px;
    }
  `,
})
export class LinhaDoTempo {
  readonly agendamentoId = input.required<string>();

  private readonly historico = httpResource<EventoDoHistorico[]>(() => {
    const id = this.agendamentoId();
    return id ? `/api/agendamentos/${id}/historico` : undefined;
  });

  /** A API já devolve em ordem cronológica. */
  protected readonly eventos = computed(() =>
    (this.historico.hasValue() ? this.historico.value() : []).map((evento) => ({
      quando: evento.quando,
      hora: evento.quando ? diaEHora(evento.quando) : '',
      frase: fraseDoEvento(evento),
    })),
  );
  protected readonly carregou = computed(() => this.historico.hasValue());
  /** Só esta seção avisa: o resto do painel segue usável. */
  protected readonly falha = computed(() =>
    falhaDe(this.historico, 'Não foi possível carregar o histórico.'),
  );
}
