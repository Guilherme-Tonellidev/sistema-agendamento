import { Agendamento } from '../../core/api/modelos';

export interface ReservasSeparadas {
  readonly proximas: readonly Agendamento[];
  readonly historico: readonly Agendamento[];
}

/**
 * Próximas: as confirmadas que ainda não terminaram, da mais perto para a mais longe. O resto
 * (concluídas, canceladas, faltas ou que já passaram) vira histórico, da mais recente para trás.
 */
export function separarReservas(reservas: readonly Agendamento[], agora: Date): ReservasSeparadas {
  const vigente = (reserva: Agendamento) =>
    reserva.status === 'CONFIRMADO' && new Date(reserva.fim) > agora;
  return {
    proximas: reservas.filter(vigente).sort((a, b) => a.inicio.localeCompare(b.inicio)),
    historico: reservas
      .filter((reserva) => !vigente(reserva))
      .sort((a, b) => b.inicio.localeCompare(a.inicio)),
  };
}
