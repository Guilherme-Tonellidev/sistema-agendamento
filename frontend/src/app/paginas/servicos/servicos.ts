import { httpResource } from '@angular/common/http';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Servico } from '../../core/api/modelos';
import { conteudo, ultimoValor } from '../../core/api/recursos';
import { FUNCIONAMENTO, horarioDe } from '../../core/barbearia';
import { Cabecalho } from '../../compartilhado/cabecalho/cabecalho';
import { DuracaoPipe, PrecoPipe } from '../../compartilhado/formatos';

/** A tabela de preços pendurada na parede, com atalho para agendar cada serviço. */
@Component({
  selector: 'app-servicos',
  imports: [RouterLink, Cabecalho, DuracaoPipe, PrecoPipe],
  templateUrl: './servicos.html',
  styleUrl: './servicos.css',
})
export class Servicos {
  protected readonly catalogo = httpResource(
    () => ({ url: '/api/servicos', params: { ativo: true, size: 50 } }),
    { parse: conteudo<Servico> },
  );
  protected readonly servicos = ultimoValor(this.catalogo, []);

  protected readonly funcionamento = FUNCIONAMENTO.map(
    (expediente) => `${expediente.rotulo} ${horarioDe(expediente)}`,
  ).join(' · ');
}
