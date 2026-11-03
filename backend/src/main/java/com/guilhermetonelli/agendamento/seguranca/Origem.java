package com.guilhermetonelli.agendamento.seguranca;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Endereço de origem de uma requisição, no formato usado como parte da chave do limite de login.
 *
 * <p>Quem tem uma rede IPv6 recebe de uma só vez um bloco /64 (2^64 endereços) e pode trocar de endereço a cada
 * tentativa. Contar por endereço completo deixaria essa pessoa fora do limite, então todo IPv6 vale pelo seu /64.
 * IPv4 segue endereço a endereço.
 */
final class Origem {

    private Origem() {}

    /**
     * IPv4 fica como veio. IPv6 vira o prefixo /64 ({@code 2001:0db8:0001:0002::/64}). IPv6 que carrega um IPv4
     * ({@code ::ffff:203.0.113.7}) vira o IPv4. Texto que não é um endereço IP volta sem mudança.
     */
    static String agrupar(String endereco) {
        if (endereco == null || endereco.indexOf(':') < 0) {
            return endereco;
        }
        int zona = endereco.indexOf('%');
        String semZona = zona < 0 ? endereco : endereco.substring(0, zona);
        try {
            // Com ':' no texto o Java o lê como IPv6 literal e nunca consulta o DNS.
            InetAddress ip = InetAddress.getByName(semZona);
            if (ip instanceof Inet6Address) {
                byte[] b = ip.getAddress();
                return "%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64".formatted(b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7]);
            }
            return ip.getHostAddress();
        } catch (UnknownHostException e) {
            return endereco;
        }
    }
}
