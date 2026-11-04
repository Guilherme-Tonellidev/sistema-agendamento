import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
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

  const botoes = () =>
    [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].map((b) =>
      b.textContent?.trim(),
    );

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PainelAtendimento],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
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
});
