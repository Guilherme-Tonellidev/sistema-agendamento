package com.guilhermetonelli.agendamento.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrigemTest {

    @Test
    void ipv4SegueEnderecoAEndereco() {
        assertThat(Origem.agrupar("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(Origem.agrupar("203.0.113.8")).isNotEqualTo(Origem.agrupar("203.0.113.7"));
    }

    @Test
    void ipv6DoMesmoBlocoSlash64VirouUmaOrigemSo() {
        String a = Origem.agrupar("2001:db8:1:2:aaaa:bbbb:cccc:dddd");
        String b = Origem.agrupar("2001:db8:1:2::1");
        String c = Origem.agrupar("2001:0DB8:0001:0002:ffff:ffff:ffff:ffff");

        assertThat(a).isEqualTo("2001:0db8:0001:0002::/64");
        assertThat(b).isEqualTo(a);
        assertThat(c).isEqualTo(a);
    }

    @Test
    void ipv6DeOutroBlocoContaSeparado() {
        assertThat(Origem.agrupar("2001:db8:1:3::1")).isNotEqualTo(Origem.agrupar("2001:db8:1:2::1"));
    }

    @Test
    void ipv6QueCarregaIpv4ViraOIpv4() {
        assertThat(Origem.agrupar("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void ignoraAZonaDeEnderecoLocal() {
        assertThat(Origem.agrupar("fe80::1%eth0")).isEqualTo(Origem.agrupar("fe80::1"));
    }

    @Test
    void textoQueNaoEEnderecoVoltaSemMudanca() {
        assertThat(Origem.agrupar("não:é:ip")).isEqualTo("não:é:ip");
        assertThat(Origem.agrupar(null)).isNull();
    }

    @Test
    void chaveJuntaOEmailNormalizadoEAOrigemAgrupada() {
        assertThat(LimiteDeTentativas.chave(" Carlos@Gmail.com", "2001:db8:1:2:aaaa:bbbb:cccc:dddd"))
                .isEqualTo(LimiteDeTentativas.chave("carlos@gmail.com", "2001:db8:1:2::9"))
                .isEqualTo("carlos@gmail.com|2001:0db8:0001:0002::/64");
        assertThat(LimiteDeTentativas.chave("carlos@gmail.com", "203.0.113.7")).isEqualTo("carlos@gmail.com|203.0.113.7");
    }
}
