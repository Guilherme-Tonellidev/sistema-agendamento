import { Component, inject, input, signal } from '@angular/core';
import {
  FormField,
  FormRoot,
  TreeValidationResult,
  email,
  form,
  required,
} from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { mensagemDeErro } from '../../core/api/erros';
import { Credenciais } from '../../core/api/modelos';
import { destinoAposEntrar } from '../../core/autenticacao/guardas';
import { Sessao } from '../../core/autenticacao/sessao';
import { Vitrine } from '../../compartilhado/vitrine/vitrine';

@Component({
  selector: 'app-entrar',
  imports: [FormField, FormRoot, RouterLink, Vitrine],
  templateUrl: './entrar.html',
  styleUrl: '../acesso.css',
})
export class Entrar {
  private readonly sessao = inject(Sessao);
  private readonly router = inject(Router);

  /** Para onde voltar depois de entrar (query param ?voltar=). */
  readonly voltar = input<string>();

  protected readonly login = form(
    signal<Credenciais>({ email: '', senha: '' }),
    (campo) => {
      required(campo.email, { message: 'Informe seu e-mail.' });
      email(campo.email, { message: 'Confira o e-mail digitado.' });
      required(campo.senha, { message: 'Informe a senha.' });
    },
    { submission: { action: (login) => this.entrar(login().value()) } },
  );

  private async entrar(credenciais: Credenciais): Promise<TreeValidationResult> {
    try {
      const usuario = await firstValueFrom(this.sessao.entrar(credenciais));
      await this.router.navigateByUrl(destinoAposEntrar(usuario, this.voltar()));
      return undefined;
    } catch (erro) {
      return {
        kind: 'servidor',
        message: mensagemDeErro(erro, 'Não foi possível entrar agora. Tente de novo.'),
      };
    }
  }
}
