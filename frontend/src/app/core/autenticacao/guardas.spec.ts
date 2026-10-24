import { Usuario } from '../api/modelos';
import { destinoAposEntrar } from './guardas';

const CLIENTE: Usuario = {
  id: 'u1',
  email: 'carlos@gmail.com',
  papel: 'CLIENTE',
  profissionalId: null,
  clienteId: 'c1',
};
const BARBEIRO: Usuario = {
  id: 'u2',
  email: 'rafael@tonelli.com.br',
  papel: 'PROFISSIONAL',
  profissionalId: 'p1',
  clienteId: null,
};

describe('destinoAposEntrar', () => {
  it('volta para a tela de onde a pessoa veio', () => {
    expect(destinoAposEntrar(CLIENTE, '/horarios?dia=2026-10-21')).toBe('/horarios?dia=2026-10-21');
  });

  it('sem destino, cada papel vai para a sua tela inicial', () => {
    expect(destinoAposEntrar(CLIENTE, undefined)).toBe('/horarios');
    expect(destinoAposEntrar(BARBEIRO, undefined)).toBe('/agenda');
  });

  it.each(['https://golpe.com', '//golpe.com', '/\\golpe.com', 'javascript:alert(1)'])(
    'não deixa o login redirecionar para fora (%s)',
    (voltar) => {
      expect(destinoAposEntrar(CLIENTE, voltar)).toBe('/horarios');
    },
  );
});
