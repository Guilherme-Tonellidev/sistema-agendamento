import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { Papel } from '../../core/api/modelos';
import { Sessao } from '../../core/autenticacao/sessao';
import { Marca } from '../marca/marca';

interface Link {
  readonly rota: string;
  readonly rotulo: string;
}

const SERVICOS: Link = { rota: '/servicos', rotulo: 'Serviços' };

/** O segundo link do topo muda com quem está logado. */
const DO_PAPEL: Record<Papel, Link> = {
  CLIENTE: { rota: '/minhas-reservas', rotulo: 'Minhas reservas' },
  PROFISSIONAL: { rota: '/agenda', rotulo: 'Minha agenda' },
  ADMIN: { rota: '/agenda', rotulo: 'Agenda' },
};

@Component({
  selector: 'app-cabecalho',
  imports: [RouterLink, RouterLinkActive, Marca],
  templateUrl: './cabecalho.html',
  styleUrl: './cabecalho.css',
})
export class Cabecalho {
  protected readonly sessao = inject(Sessao);

  protected readonly links = computed(() => {
    const papel = this.sessao.usuario()?.papel;
    return papel ? [SERVICOS, DO_PAPEL[papel]] : [SERVICOS];
  });
  protected readonly inicial = computed(
    () => this.sessao.usuario()?.email.charAt(0).toUpperCase() ?? '',
  );
}
