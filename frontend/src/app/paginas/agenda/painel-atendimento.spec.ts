import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Agendamento } from '../../core/api/modelos';
import { PainelAtendimento } from './painel-atendimento';

/** Das 10:00 às 10:30 em São Paulo. */
const EM_ANDAMENTO: Agendamento = {
  id: 'a1',
  inicio: '2026-11-10T13:00:00Z',
  fim: '2026-11-10T13:30:00Z',
  preco: 50,
  status: 'CONFIRMADO',
  canceladoEm: null,
  motivoCancelamento: null,
  profissional: { id: 'p1', nome: 'Rafael', ativo: true },
  servico: { id: 's1', nome: 'Corte', duracaoMinutos: 30, preco: 50 },
  cliente: { id: 'c1', nome: 'Carlos Mendes', telefone: null },
};
const NO_MEIO = new Date('2026-11-10T13:10:00Z');

describe('PainelAtendimento', () => {
  let fixture: ComponentFixture<PainelAtendimento>;
  let servidor: HttpTestingController;

  const tela = () => fixture.nativeElement as HTMLElement;
  const botoes = () => [...tela().querySelectorAll('button')].map((b) => b.textContent?.trim());
  const botao = (texto: string) =>
    [...tela().querySelectorAll('button')].find((b) => b.textContent?.trim() === texto)!;
  /** Com requisição pendente o whenStable esperaria por ela: aqui quem responde é o teste. */
  const assentar = async () => {
    await new Promise<void>((pronto) => setTimeout(pronto));
    TestBed.tick();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PainelAtendimento],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servidor = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PainelAtendimento);
    fixture.componentRef.setInput('agendamento', EM_ANDAMENTO);
    fixture.componentRef.setInput('agora', NO_MEIO);
  });

  it('para o administrador, o atendimento em andamento também pode ser cancelado', async () => {
    fixture.componentRef.setInput('cancelaDepoisDoInicio', true);
    await fixture.whenStable();

    expect(botoes()).toContain('Cancelar e avisar o cliente');
    expect(botoes()).toContain('Concluir atendimento');
  });

  it('na agenda do barbeiro (padrão), em andamento só conclui ou registra a falta', async () => {
    await fixture.whenStable();

    expect(botoes()).not.toContain('Cancelar e avisar o cliente');
    expect(botoes()).toContain('Concluir atendimento');
    expect(botoes()).toContain('Cliente não compareceu');
  });

  it('depois de cancelar, devolve o agendamento do servidor; com ele, oferece avisar no WhatsApp', async () => {
    const comTelefone = {
      ...EM_ANDAMENTO,
      cliente: { ...EM_ANDAMENTO.cliente, telefone: '14998123344' },
    };
    fixture.componentRef.setInput('agendamento', comTelefone);
    fixture.componentRef.setInput('agora', new Date('2026-11-10T12:00:00Z'));
    const mudancas: Agendamento[] = [];
    fixture.componentInstance.mudou.subscribe((agendamento) => mudancas.push(agendamento));
    await assentar();

    botao('Cancelar e avisar o cliente').click();
    await assentar();
    botao('Sim, cancelar').click();
    await assentar();
    const cancelado = { ...comTelefone, status: 'CANCELADO' as const };
    servidor.expectOne({ method: 'PATCH', url: '/api/agendamentos/a1/cancelar' }).flush(cancelado);
    await assentar();
    // Como a Semana faz: o painel passa a mostrar o agendamento devolvido.
    fixture.componentRef.setInput('agendamento', mudancas[0]);
    await assentar();

    expect(mudancas).toEqual([cancelado]);
    expect(tela().querySelector('a')?.textContent).toContain('Avisar no WhatsApp');
  });
});
