import { HttpClient, httpResource } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { mensagemDeErro } from '../../core/api/erros';
import { Agendamento, StatusAgendamento } from '../../core/api/modelos';
import { conteudo, ultimoValor } from '../../core/api/recursos';
import { PRAZO_PARA_MUDAR_EM_HORAS, limiteParaMudar } from '../../core/barbearia';
import {
  comMaiuscula,
  dataNoFuso,
  dataPorExtenso,
  diaDaSemanaPorExtenso,
  horaNoFuso,
  rotuloDoDia,
} from '../../core/datas';
import { Cabecalho } from '../../compartilhado/cabecalho/cabecalho';
import { Ingresso } from '../../compartilhado/ingresso/ingresso';
import { separarReservas } from './linha-do-tempo';

const SITUACAO: Record<StatusAgendamento, string> = {
  CONFIRMADO: 'Confirmado',
  CONCLUIDO: 'Concluído',
  CANCELADO: 'Cancelado',
  NAO_COMPARECEU: 'Você não compareceu',
};

@Component({
  selector: 'app-reservas',
  imports: [RouterLink, Cabecalho, Ingresso],
  templateUrl: './reservas.html',
  styleUrl: './reservas.css',
})
export class Reservas {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  protected readonly lista = httpResource(
    () => ({ url: '/api/agendamentos', params: { size: 50, sort: 'inicio,desc' } }),
    { parse: conteudo<Agendamento> },
  );
  private readonly reservas = ultimoValor(this.lista, []);
  private readonly separadas = computed(() => separarReservas(this.reservas(), new Date()));

  protected readonly cartoes = computed(() => {
    const agora = new Date();
    const hoje = dataNoFuso(agora);
    return this.separadas().proximas.map((reserva) => {
      const dia = dataNoFuso(reserva.inicio);
      const limite = limiteParaMudar(reserva.inicio);
      const diaDoLimite = dataNoFuso(limite);
      const quando = diaDoLimite === hoje ? 'hoje' : diaDaSemanaPorExtenso(diaDoLimite);
      return {
        reserva,
        hoje: dia === hoje,
        dia: rotuloDoDia(dia, hoje),
        hora: horaNoFuso(reserva.inicio),
        fim: horaNoFuso(reserva.fim),
        podeMudar: agora < limite,
        limite: `${quando}, ${horaNoFuso(limite)}`,
      };
    });
  });

  protected readonly historico = computed(() =>
    this.separadas().historico.map((reserva) => ({
      reserva,
      dia: comMaiuscula(dataPorExtenso(dataNoFuso(reserva.inicio))),
      hora: horaNoFuso(reserva.inicio),
      situacao: SITUACAO[reserva.status],
    })),
  );

  protected readonly prazo = PRAZO_PARA_MUDAR_EM_HORAS;
  /** A reserva cujo cancelamento a pessoa está confirmando. */
  protected readonly confirmando = signal<string | null>(null);
  protected readonly cancelando = signal(false);
  protected readonly erroAoCancelar = signal<string | null>(null);

  protected pedirCancelamento(id: string | null): void {
    this.confirmando.set(id);
    this.erroAoCancelar.set(null);
  }

  protected async cancelar({ id }: Agendamento): Promise<void> {
    this.cancelando.set(true);
    try {
      await firstValueFrom(this.http.patch(`/api/agendamentos/${id}/cancelar`, null));
      this.confirmando.set(null);
      this.lista.reload();
    } catch (erro) {
      this.erroAoCancelar.set(
        mensagemDeErro(erro, 'Não foi possível cancelar agora. Tente de novo.'),
      );
    } finally {
      this.cancelando.set(false);
    }
  }

  /** Remarcar é escolher outro horário com o mesmo barbeiro e serviço. */
  protected remarcar({ id, servico, profissional }: Agendamento): void {
    void this.router.navigate(['/horarios'], {
      queryParams: { servico: servico.id, barbeiro: profissional.id, remarcar: id },
    });
  }
}
