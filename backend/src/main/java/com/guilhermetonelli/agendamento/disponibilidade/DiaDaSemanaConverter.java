package com.guilhermetonelli.agendamento.disponibilidade;

import java.time.DayOfWeek;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Grava o dia da semana no padrão ISO (1 = segunda ... 7 = domingo), e não na posição do enum. */
@Converter
class DiaDaSemanaConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek dia) {
        return dia == null ? null : (short) dia.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short valor) {
        return valor == null ? null : DayOfWeek.of(valor);
    }
}
