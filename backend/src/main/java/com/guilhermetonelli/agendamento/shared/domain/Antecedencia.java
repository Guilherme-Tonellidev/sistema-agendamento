package com.guilhermetonelli.agendamento.shared.domain;

import java.time.Duration;

/**
 * Quanto tempo antes do início uma ação sobre um agendamento precisa acontecer. {@code livre} dispensa o prazo
 * e a regra do futuro (o administrador pode mexer no passado e além da janela de reserva); o resto das regras vale
 * para todos.
 */
public record Antecedencia(Duration minima, boolean livre) {

    /** Sem prazo, mas só no futuro: a equipe não reserva, cancela nem reagenda o que já começou. */
    public static final Antecedencia NENHUMA = new Antecedencia(Duration.ZERO, false);

    public static final Antecedencia LIVRE = new Antecedencia(Duration.ZERO, true);

    public static Antecedencia de(Duration minima) {
        return new Antecedencia(minima, false);
    }
}
