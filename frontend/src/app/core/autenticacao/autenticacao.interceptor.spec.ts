import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { autenticacaoInterceptor } from './autenticacao.interceptor';
import { Sessao } from './sessao';

const CLIENTE = {
  id: 'u1',
  email: 'carlos@gmail.com',
  papel: 'CLIENTE',
  profissionalId: null,
  clienteId: 'c1',
};

describe('autenticacaoInterceptor', () => {
  let http: HttpClient;
  let servidor: HttpTestingController;
  let sessao: Sessao;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'entrar', children: [] }]),
        provideHttpClient(withInterceptors([autenticacaoInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    servidor = TestBed.inject(HttpTestingController);
    sessao = TestBed.inject(Sessao);

    sessao.entrar({ email: CLIENTE.email, senha: 'senha-segura-123' }).subscribe();
    servidor
      .expectOne('/api/auth/login')
      .flush({ tokenDeAcesso: 'vencido', tipo: 'Bearer', expiraEmSegundos: 900 });
    servidor.expectOne('/api/auth/eu').flush(CLIENTE);
  });

  afterEach(() => servidor.verify());

  it('não manda o token nas rotas que abrem ou renovam a sessão', () => {
    http.post('/api/auth/refresh', null).subscribe();

    expect(servidor.expectOne('/api/auth/refresh').request.headers.has('Authorization')).toBe(
      false,
    );
  });

  it('num 401, renova a sessão uma vez e repete a chamada com o token novo', () => {
    let resposta: unknown;
    http.get('/api/agendamentos').subscribe((corpo) => (resposta = corpo));

    const primeira = servidor.expectOne('/api/agendamentos');
    expect(primeira.request.headers.get('Authorization')).toBe('Bearer vencido');
    primeira.flush(null, { status: 401, statusText: 'Unauthorized' });

    servidor
      .expectOne('/api/auth/refresh')
      .flush({ tokenDeAcesso: 'novo', tipo: 'Bearer', expiraEmSegundos: 900 });

    const repetida = servidor.expectOne('/api/agendamentos');
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer novo');
    repetida.flush({ content: [] });

    expect(resposta).toEqual({ content: [] });
  });

  it('se a renovação falhar, encerra a sessão', () => {
    http.get('/api/agendamentos').subscribe({ error: () => undefined });

    servidor
      .expectOne('/api/agendamentos')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    servidor
      .expectOne('/api/auth/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(sessao.logado()).toBe(false);
  });
});
