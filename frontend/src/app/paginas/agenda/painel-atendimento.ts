import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { mensagemDeErro } from '../../core/api/erros';
import { Agendamento } from '../../core/api/modelos';
import { dataNoFuso, dataPorExtenso, horaNoFuso } from '../../core/datas';
import { Ingresso } from '../../compartilhado/ingresso/ingresso';
import { TelefonePipe, linkDoWhatsApp } from '../../compartilhado/telefone';
import { Estado, estadoDe } from './fila';

const SITUACAO: Record<Estado, string> = {
  confirmado: 'Confirmado',
  andamento: 'Em andamento',
  pendente: 'Começou, aguardando você encerrar',
  concluido: 'Concluído',
  faltou: 'Não compareceu',
  cancelado: 'Cancelado',
};

const primeiroNome = (nome: string) => nome.trim().split(/\s+/)[0];

/** O atendimento escolhido na fila: dados do cliente e o que o barbeiro pode fazer com ele. */
@Component({
  selector: 'app-painel-atendimento',
  imports: [CurrencyPipe, Ingresso, TelefonePipe],
  templateUrl: './painel-atendimento.html',
  styleUrl: './painel.css',
})
export class PainelAtendimento {
  private readonly http = inject(HttpClient);

  readonly agendamento = input.required<Agendamento>();
  readonly agora = input.required<Date>();
  /** O administrador cancela mesmo depois do início; a /agenda do barbeiro não. */
  readonly cancelaDepoisDoInicio = input(false);
  /** Concluído, falta registrada ou cancelado, como o servidor devolveu (a agenda só recarrega). */
  readonly mudou = output<Agendamento>();

  protected readonly estado = computed(() => estadoDe(this.agendamento(), this.agora()));
  protected readonly situacao = computed(() => SITUACAO[this.estado()]);
  protected readonly horario = computed(() => {
    const { inicio, fim, servico } = this.agendamento();
    return {
      inicio: horaNoFuso(inicio),
      fim: horaNoFuso(fim),
      servico: servico.nome.toLowerCase(),
    };
  });

  /** Recomeça a cada atendimento escolhido. */
  protected readonly confirmando = linkedSignal({
    source: () => this.agendamento().id,
    computation: () => false,
  });
  protected readonly erro = linkedSignal<string, string | null>({
    source: () => this.agendamento().id,
    computation: () => null,
  });
  protected readonly enviando = signal(false);

  /** Conversa já começada; depois de cancelar, a mensagem avisa o cliente. Encerrados não têm. */
  protected readonly whatsapp = computed(() => {
    const { cliente, profissional, inicio } = this.agendamento();
    const estado = this.estado();
    if (!cliente.telefone || estado === 'concluido' || estado === 'faltou') {
      return null;
    }
    const ola = `Olá, ${primeiroNome(cliente.nome)}! Aqui é o ${primeiroNome(profissional.nome)}, da Barbearia Tonelli.`;
    if (estado !== 'cancelado') {
      return linkDoWhatsApp(cliente.telefone, ola);
    }
    const dia = dataNoFuso(inicio);
    const quando = dia === dataNoFuso(this.agora()) ? 'hoje' : dataPorExtenso(dia);
    return linkDoWhatsApp(
      cliente.telefone,
      `${ola} Precisei cancelar seu horário de ${quando} às ${horaNoFuso(inicio)}. Quer marcar outro?`,
    );
  });

  protected async enviar(acao: 'concluir' | 'nao-compareceu' | 'cancelar'): Promise<void> {
    this.enviando.set(true);
    this.erro.set(null);
    try {
      const agendamento = await firstValueFrom(
        this.http.patch<Agendamento>(`/api/agendamentos/${this.agendamento().id}/${acao}`, null),
      );
      this.confirmando.set(false);
      this.mudou.emit(agendamento);
    } catch (erro) {
      this.erro.set(mensagemDeErro(erro, 'Não foi possível salvar agora. Tente de novo.'));
    } finally {
      this.enviando.set(false);
    }
  }
}
