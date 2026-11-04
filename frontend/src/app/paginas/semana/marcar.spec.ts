import { HorarioLivre } from '../../core/api/modelos';
import { escolherHorario } from './marcar';

const livre = (inicio: string): HorarioLivre => ({
  inicio,
  fim: new Date(Date.parse(inicio) + 30 * 60_000).toISOString(),
});

const livres = [
  livre('2026-11-10T12:00:00Z'),
  livre('2026-11-10T12:30:00Z'),
  livre('2026-11-10T14:00:00Z'),
];

describe('escolherHorario', () => {
  it('devolve o horário que começa no instante clicado', () => {
    expect(escolherHorario(livres, new Date('2026-11-10T12:30:00Z'))).toBe(livres[1]);
  });

  it('sem horário no instante clicado, devolve o primeiro depois dele', () => {
    expect(escolherHorario(livres, new Date('2026-11-10T12:45:00Z'))).toBe(livres[2]);
  });

  it('clique depois de todos os horários devolve o primeiro do dia', () => {
    expect(escolherHorario(livres, new Date('2026-11-10T19:00:00Z'))).toBe(livres[0]);
  });

  it('sem horários livres não há o que escolher', () => {
    expect(escolherHorario([], new Date('2026-11-10T12:00:00Z'))).toBeNull();
  });
});
