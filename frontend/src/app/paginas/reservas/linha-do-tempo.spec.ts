import { Agendamento, StatusAgendamento } from '../../core/api/modelos';
import { separarReservas } from './linha-do-tempo';

function reserva(
  id: string,
  inicio: string,
  status: StatusAgendamento = 'CONFIRMADO',
): Agendamento {
  const fim = new Date(new Date(inicio).getTime() + 30 * 60 * 1000).toISOString();
  return {
    id,
    inicio,
    fim,
    preco: 45,
    status,
    canceladoEm: null,
    motivoCancelamento: null,
    profissional: { id: 'p1', nome: 'Rafael Souza', ativo: true },
    servico: { id: 's1', nome: 'Corte', duracaoMinutos: 30, preco: 45 },
    cliente: { id: 'c1', nome: 'Carlos Lima', telefone: null },
  };
}

describe('separarReservas', () => {
  const agora = new Date('2026-10-21T15:10:00Z');

  it('próximas: as confirmadas que não terminaram, da mais perto para a mais longe', () => {
    const { proximas } = separarReservas(
      [
        reserva('sabado', '2026-10-24T13:00:00Z'),
        reserva('em-andamento', '2026-10-21T15:00:00Z'),
        reserva('amanha', '2026-10-22T12:00:00Z'),
        reserva('cancelada', '2026-10-23T12:00:00Z', 'CANCELADO'),
      ],
      agora,
    );

    expect(proximas.map(({ id }) => id)).toEqual(['em-andamento', 'amanha', 'sabado']);
  });

  it('o histórico junta o que já passou e o que foi encerrado, do mais recente para trás', () => {
    const { historico } = separarReservas(
      [
        reserva('semana-passada', '2026-10-14T12:30:00Z', 'CONCLUIDO'),
        reserva('cancelada', '2026-10-23T12:00:00Z', 'CANCELADO'),
        reserva('ontem-sem-baixa', '2026-10-20T12:00:00Z'),
        reserva('faltou', '2026-09-26T13:15:00Z', 'NAO_COMPARECEU'),
      ],
      agora,
    );

    expect(historico.map(({ id }) => id)).toEqual([
      'cancelada',
      'ontem-sem-baixa',
      'semana-passada',
      'faltou',
    ]);
  });
});
