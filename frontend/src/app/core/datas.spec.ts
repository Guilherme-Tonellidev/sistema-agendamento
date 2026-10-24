import { dataNoFuso, horaNoFuso, instanteNoFuso, rotuloDoDia, somarDias } from './datas';

describe('datas no fuso da barbearia', () => {
  it('um instante perto da meia-noite cai no dia e na hora de São Paulo', () => {
    const instante = '2026-10-22T02:30:00Z'; // 23:30 do dia 21 em São Paulo

    expect(dataNoFuso(instante)).toBe('2026-10-21');
    expect(horaNoFuso(instante)).toBe('23:30');
  });

  it('a hora do relógio da barbearia vira o instante certo, mesmo trocando o dia em UTC', () => {
    expect(instanteNoFuso('2026-10-21', '15:00').toISOString()).toBe('2026-10-21T18:00:00.000Z');
    expect(instanteNoFuso('2026-10-21', '23:30').toISOString()).toBe('2026-10-22T02:30:00.000Z');
  });

  it('soma dias atravessando o fim do mês', () => {
    expect(somarDias('2026-10-31', 1)).toBe('2026-11-01');
  });

  it('chama hoje e amanhã pelo nome; os outros dias, por extenso', () => {
    expect(rotuloDoDia('2026-10-21', '2026-10-21')).toBe('Hoje, quarta 21');
    expect(rotuloDoDia('2026-10-22', '2026-10-21')).toBe('Amanhã, quinta 22');
    expect(rotuloDoDia('2026-10-24', '2026-10-21')).toBe('Sábado, 24 de outubro');
  });
});
