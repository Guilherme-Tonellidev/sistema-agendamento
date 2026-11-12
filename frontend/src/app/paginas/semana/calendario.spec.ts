import { TestBed } from '@angular/core/testing';
import { Calendar } from '@fullcalendar/core';

import { Calendario } from './calendario';

describe('Calendario', () => {
  let avisos: Array<() => void>;
  let desconexoes: number;

  beforeEach(() => {
    avisos = [];
    desconexoes = 0;
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(aviso: () => void) {
          avisos.push(aviso);
        }
        observe() {}
        disconnect() {
          desconexoes++;
        }
      },
    );
    // O quadro seguinte roda na hora: o teste não espera o navegador.
    vi.stubGlobal('requestAnimationFrame', (rodar: () => void) => {
      rodar();
      return 1;
    });
    vi.stubGlobal('cancelAnimationFrame', () => undefined);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  async function montar() {
    const fixture = TestBed.createComponent(Calendario);
    fixture.componentRef.setInput('eventos', []);
    fixture.componentRef.setInput('dia', '2026-11-12');
    await fixture.whenStable();
    TestBed.tick();
    return fixture;
  }

  it('refaz o tamanho da grade quando o contêiner muda de largura', async () => {
    await montar();
    const atualizar = vi.spyOn(Calendar.prototype, 'updateSize');

    avisos.forEach((aviso) => aviso());

    expect(atualizar).toHaveBeenCalled();
  });

  it('para de observar o contêiner quando a grade sai da tela', async () => {
    const fixture = await montar();

    fixture.destroy();

    expect(desconexoes).toBeGreaterThan(0);
  });
});
