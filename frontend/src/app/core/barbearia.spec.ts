import { abertaEm, atendeEm } from './barbearia';

/** Instante no horário de São Paulo. */
const as = (dataHora: string) => new Date(`${dataHora}:00-03:00`);

describe('abertaEm', () => {
  it('acende o neon só no horário da casa', () => {
    expect(abertaEm(as('2026-10-24T07:59'))).toBe(false); // sábado
    expect(abertaEm(as('2026-10-24T08:00'))).toBe(true);
    expect(abertaEm(as('2026-10-24T18:00'))).toBe(false);
    expect(abertaEm(as('2026-10-25T10:00'))).toBe(false); // domingo
  });
});

describe('atendeEm', () => {
  it('hoje, só até a casa fechar', () => {
    expect(atendeEm('2026-10-22', as('2026-10-22T19:59'))).toBe(true);
    expect(atendeEm('2026-10-22', as('2026-10-22T20:00'))).toBe(false);
    expect(atendeEm('2026-10-23', as('2026-10-22T20:00'))).toBe(true);
  });

  it('domingo não atende', () => {
    expect(atendeEm('2026-10-25', as('2026-10-22T10:00'))).toBe(false);
  });
});
