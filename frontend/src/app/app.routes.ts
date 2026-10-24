import { Routes } from '@angular/router';

import { exigePapel, somenteVisitante } from './core/autenticacao/guardas';

const emBreve = () => import('./paginas/em-breve/em-breve').then((m) => m.EmBreve);

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'horarios' },
  {
    path: 'entrar',
    title: 'Entrar — Barbearia Tonelli',
    canActivate: [somenteVisitante],
    loadComponent: () => import('./paginas/entrar/entrar').then((m) => m.Entrar),
  },
  {
    path: 'cadastro',
    title: 'Criar conta — Barbearia Tonelli',
    canActivate: [somenteVisitante],
    loadComponent: () => import('./paginas/cadastro/cadastro').then((m) => m.Cadastro),
  },
  {
    path: 'horarios',
    title: 'Escolher horário — Barbearia Tonelli',
    loadComponent: () => import('./paginas/horarios/horarios').then((m) => m.Horarios),
  },
  {
    path: 'servicos',
    title: 'Serviços — Barbearia Tonelli',
    data: { titulo: 'Serviços' },
    loadComponent: emBreve,
  },
  {
    path: 'minhas-reservas',
    title: 'Minhas reservas — Barbearia Tonelli',
    data: { titulo: 'Minhas reservas' },
    canActivate: [exigePapel('CLIENTE')],
    loadComponent: emBreve,
  },
  {
    path: 'agenda',
    title: 'Minha agenda — Barbearia Tonelli',
    data: { titulo: 'Minha agenda' },
    canActivate: [exigePapel('PROFISSIONAL', 'ADMIN')],
    loadComponent: emBreve,
  },
  { path: '**', redirectTo: '' },
];
