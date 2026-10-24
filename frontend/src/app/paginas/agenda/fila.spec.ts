import { Agendamento, StatusAgendamento } from '../../core/api/modelos';
import { estadoDe, juntarEmVaos, montarFila, resumirDia } from './fila';

/** Quarta, 21/10/2026: as horas abaixo são de São Paulo (UTC-3). */
const as = (hora: string) => new Date(`2026-10-21T${hora}:00-03:00`).toISOString();

function atendimento(
  id: string,
  inicio: string,
  status: StatusAgendamento = 'CONFIRMADO',
  preco = 45,
): Agendamento {
  return {
    id,
    inicio: as(inicio),
    fim: new Date(Date.parse(as(inicio)) + 30 * 60_000).toISOString(),
    preco,
    status,
    canceladoEm: null,
    motivoCancelamento: null,
    profissional: { id: 'p1', nome: 'Ana Souza', ativo: true },
    servico: { id: 's1', nome: 'Corte', duracaoMinutos: 30, preco },
    cliente: { id: `c-${id}`, nome: id, telefone: null },
  };
}

describe('juntarEmVaos', () => {
  it('junta horários seguidos ou sobrepostos e separa o que tem atendimento no meio', () => {
    const livres = [
      { inicio: as('16:30'), fim: as('17:00') },
      { inicio: as('15:15'), fim: as('15:45') },
      { inicio: as('15:30'), fim: as('16:00') },
      { inicio: as('16:45'), fim: as('17:15') },
    ];

    expect(juntarEmVaos(livres)).toEqual([
      { inicio: as('15:15'), fim: as('16:00') },
      { inicio: as('16:30'), fim: as('17:15') },
    ]);
  });
});

describe('montarFila', () => {
  const agora = new Date(as('15:10'));
  const fila = montarFila(
    [
      atendimento('marcos', '16:00'),
      atendimento('julia', '14:30'),
      atendimento('carlos', '09:30', 'CONCLUIDO'),
      atendimento('diana', '10:00', 'NAO_COMPARECEU'),
      atendimento('cancelado', '17:00', 'CANCELADO'),
    ],
    [
      { id: 'almoco', inicio: as('12:00'), fim: as('13:00'), motivo: 'Almoço' },
      { id: 'consulta', inicio: as('19:00'), fim: as('20:00'), motivo: null },
    ],
    [
      { inicio: as('15:15'), fim: as('16:00') },
      { inicio: as('16:30'), fim: as('17:30') },
    ],
    agora,
  );

  it('em "agora" fica o que já começou e não foi encerrado', () => {
    expect(fila.agora.map(({ id }) => id)).toEqual(['julia']);
  });

  it('nos próximos, atendimentos, vãos e bloqueios em ordem; cancelados e o que passou saem', () => {
    expect(fila.proximos.map(({ tipo, inicio }) => `${tipo} ${inicio}`)).toEqual([
      `vao ${as('15:15')}`,
      `atendimento ${as('16:00')}`,
      `vao ${as('16:30')}`,
      `bloqueio ${as('19:00')}`,
    ]);
  });

  it('encerrados são os concluídos e as faltas, pela ordem do dia', () => {
    expect(fila.encerrados.map(({ id }) => id)).toEqual(['carlos', 'diana']);
  });
});

describe('estadoDe', () => {
  it('um confirmado está em andamento até o fim do horário e depois fica pendente', () => {
    const julia = atendimento('julia', '14:30');

    expect(estadoDe(julia, new Date(as('14:00')))).toBe('confirmado');
    expect(estadoDe(julia, new Date(as('14:45')))).toBe('andamento');
    expect(estadoDe(julia, new Date(as('15:10')))).toBe('pendente');
    expect(estadoDe(atendimento('diana', '10:00', 'NAO_COMPARECEU'), new Date(as('15:10')))).toBe(
      'faltou',
    );
  });
});

describe('resumirDia', () => {
  it('conta concluídos sobre o total do dia e prevê só o que não faltou', () => {
    expect(
      resumirDia([
        atendimento('a', '09:00', 'CONCLUIDO', 45),
        atendimento('b', '10:00', 'NAO_COMPARECEU', 35),
        atendimento('c', '11:00', 'CONFIRMADO', 70),
        atendimento('d', '12:00', 'CANCELADO', 45),
      ]),
    ).toEqual({ concluidos: 1, total: 3, previsto: 115 });
  });
});
