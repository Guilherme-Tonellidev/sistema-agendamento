import { linksDo } from './cabecalho';

const rotulos = (papel: Parameters<typeof linksDo>[0]) => linksDo(papel).map((l) => l.rotulo);

describe('linksDo', () => {
  it('dá ao administrador a agenda e a semana', () => {
    expect(linksDo('ADMIN')).toEqual([
      { rota: '/servicos', rotulo: 'Serviços' },
      { rota: '/agenda', rotulo: 'Agenda' },
      { rota: '/semana', rotulo: 'Semana' },
    ]);
  });

  it('dá ao barbeiro a própria agenda', () => {
    expect(rotulos('PROFISSIONAL')).toEqual(['Serviços', 'Minha agenda']);
  });

  it('dá ao cliente as reservas dele', () => {
    expect(rotulos('CLIENTE')).toEqual(['Serviços', 'Minhas reservas']);
  });

  it('mostra só os serviços a quem não entrou', () => {
    expect(rotulos(undefined)).toEqual(['Serviços']);
  });
});
