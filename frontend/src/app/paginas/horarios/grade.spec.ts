import { horaNoFuso } from '../../core/datas';
import { agruparPorPeriodo, primeiroLivrePorHorario } from './grade';

describe('agruparPorPeriodo', () => {
  it('separa manhã, tarde e noite pela hora da barbearia, não pela do navegador', () => {
    const periodos = agruparPorPeriodo([
      { inicio: '2026-10-21T13:00:00Z' }, // 10:00 em São Paulo
      { inicio: '2026-10-21T14:45:00Z' }, // 11:45
      { inicio: '2026-10-21T15:00:00Z' }, // 12:00
      { inicio: '2026-10-21T21:30:00Z' }, // 18:30
    ]);

    expect(periodos.map(({ nome, horarios }) => [nome, horarios.length])).toEqual([
      ['Manhã', 2],
      ['Tarde', 1],
      ['Noite', 1],
    ]);
  });

  it('mantém os três períodos mesmo sem horários', () => {
    expect(agruparPorPeriodo([]).map(({ nome }) => nome)).toEqual(['Manhã', 'Tarde', 'Noite']);
  });
});

describe('primeiroLivrePorHorario', () => {
  it('sem preferência, cada horário fica com o primeiro barbeiro livre, em ordem', () => {
    const rafael = [
      { inicio: '2026-10-21T12:00:00Z', barbeiro: 'Rafael' },
      { inicio: '2026-10-21T13:30:00Z', barbeiro: 'Rafael' },
    ];
    const bruno = [
      { inicio: '2026-10-21T12:00:00Z', barbeiro: 'Bruno' },
      { inicio: '2026-10-21T12:30:00Z', barbeiro: 'Bruno' },
    ];

    const grade = primeiroLivrePorHorario([rafael, bruno]);

    expect(grade.map(({ inicio, barbeiro }) => `${horaNoFuso(inicio)} ${barbeiro}`)).toEqual([
      '09:00 Rafael',
      '09:30 Bruno',
      '10:30 Rafael',
    ]);
  });
});
