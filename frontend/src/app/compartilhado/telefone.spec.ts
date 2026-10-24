import { TelefonePipe, linkDoWhatsApp, soDigitos } from './telefone';

describe('telefone', () => {
  it('guarda só os dígitos do que a pessoa digitou', () => {
    expect(soDigitos('(14) 99812-3344')).toBe('14998123344');
  });

  it('mostra celular e fixo com DDD', () => {
    const telefone = new TelefonePipe();

    expect(telefone.transform('14998123344')).toBe('(14) 99812-3344');
    expect(telefone.transform('1438123344')).toBe('(14) 3812-3344');
  });

  it('abre a conversa do WhatsApp com o código do Brasil e a mensagem pronta', () => {
    expect(linkDoWhatsApp('14998123344', 'Olá, Júlia!')).toBe(
      'https://wa.me/5514998123344?text=Ol%C3%A1%2C%20J%C3%BAlia!',
    );
  });
});
