import { HttpClient, httpResource } from '@angular/common/http';
import { Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { outputFromObservable, toObservable } from '@angular/core/rxjs-interop';
import {
  FormField,
  FormRoot,
  TreeValidationResult,
  form,
  maxLength,
  required,
} from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';

import { errosDoEnvio } from '../../core/api/erros';
import { Agendamento, HorarioLivre, NovoEncaixe, ServicoResumo } from '../../core/api/modelos';
import { ultimoValor } from '../../core/api/recursos';
import { horaNoFuso } from '../../core/datas';
import { Ingresso } from '../../compartilhado/ingresso/ingresso';
import { soDigitos, telefoneOpcional } from '../../compartilhado/telefone';

/** Quantos horários aparecem de cada vez para escolher. */
const HORARIOS_VISIVEIS = 8;

/** Quem chegou sem reserva: nome, serviço e um dos próximos horários livres do barbeiro. */
@Component({
  selector: 'app-painel-encaixe',
  imports: [FormField, FormRoot, Ingresso],
  templateUrl: './painel-encaixe.html',
  styleUrl: './painel.css',
})
export class PainelEncaixe {
  private readonly http = inject(HttpClient);

  readonly profissionalId = input.required<string>();
  readonly dia = input.required<string>();
  readonly servicos = input.required<readonly ServicoResumo[]>();
  /** Início desejado, vindo do vão clicado na fila (ou nenhum: o primeiro livre). */
  readonly desejado = input<string | null>(null);
  readonly encaixado = output<Agendamento>();
  readonly voltar = output<void>();

  /** Sem serviço pré-escolhido: um encaixe com o serviço errado ocupa a agenda errada. */
  protected readonly servicoId = signal<string | null>(null);
  protected readonly servico = computed(
    () => this.servicos().find(({ id }) => id === this.servicoId()) ?? null,
  );

  /** Sem antecedência mínima: os horários começam agora. */
  private readonly livres = httpResource<HorarioLivre[]>(() => {
    const servico = this.servico();
    return servico
      ? {
          url: `/api/profissionais/${this.profissionalId()}/horarios-livres/encaixe`,
          params: { servicoId: servico.id, data: this.dia() },
        }
      : undefined;
  });
  private readonly horarios = ultimoValor(this.livres, []);
  protected readonly buscando = this.livres.isLoading;

  /** Recomeça no desejado; muda quando a pessoa toca em outro horário. */
  protected readonly inicio = linkedSignal(() => this.desejado());
  /** O início pedido, se o serviço couber nele; senão, o primeiro livre depois dele. */
  protected readonly escolhido = computed(() => {
    const horarios = this.horarios();
    const pedido = Date.parse(this.inicio() ?? '');
    return horarios.find(({ inicio }) => Date.parse(inicio) >= pedido) ?? horarios[0] ?? null;
  });
  /** O horário que vai ser encaixado, para a agenda destacar o vão dele. */
  readonly horario = outputFromObservable(
    toObservable(computed(() => this.escolhido()?.inicio ?? null)),
  );
  /** Em páginas fixas, para os botões não trocarem de lugar enquanto a pessoa escolhe. */
  protected readonly opcoes = computed(() => {
    const horarios = this.horarios();
    const escolhido = this.escolhido();
    const pagina = escolhido ? Math.floor(horarios.indexOf(escolhido) / HORARIOS_VISIVEIS) : 0;
    const desde = pagina * HORARIOS_VISIVEIS;
    return horarios
      .slice(desde, desde + HORARIOS_VISIVEIS)
      .map((horario) => ({ ...horario, hora: horaNoFuso(horario.inicio) }));
  });
  protected readonly bilhete = computed(() => {
    const escolhido = this.escolhido();
    return escolhido
      ? { inicio: horaNoFuso(escolhido.inicio), fim: horaNoFuso(escolhido.fim) }
      : { inicio: '--:--', fim: '--:--' };
  });

  protected readonly cliente = form(
    signal({ nomeDoCliente: '', telefone: '' }),
    (campo) => {
      required(campo.nomeDoCliente, { message: 'Informe o nome de quem chegou.' });
      maxLength(campo.nomeDoCliente, 120, { message: 'Use até 120 caracteres.' });
      telefoneOpcional(campo.telefone);
    },
    { submission: { action: () => this.encaixar() } },
  );

  private async encaixar(): Promise<TreeValidationResult> {
    const horario = this.escolhido();
    const servico = this.servico();
    if (!horario || !servico) {
      return { kind: 'horario', message: 'Não há horário livre para esse serviço.' };
    }
    const { nomeDoCliente, telefone } = this.cliente().value();
    const encaixe: NovoEncaixe = {
      profissionalId: this.profissionalId(),
      servicoId: servico.id,
      inicio: horario.inicio,
      nomeDoCliente: nomeDoCliente.trim(),
      telefone: soDigitos(telefone) || null,
    };
    try {
      this.encaixado.emit(
        await firstValueFrom(this.http.post<Agendamento>('/api/agendamentos/encaixe', encaixe)),
      );
      return undefined;
    } catch (erro) {
      // Se alguém pegou o horário antes, a lista se atualiza.
      this.livres.reload();
      return errosDoEnvio(erro, this.cliente, 'Não foi possível encaixar agora. Tente de novo.');
    }
  }
}
