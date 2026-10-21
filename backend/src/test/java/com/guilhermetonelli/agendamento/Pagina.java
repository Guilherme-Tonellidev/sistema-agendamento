package com.guilhermetonelli.agendamento;

import java.util.List;

/** Formato das respostas paginadas da API, para ler nos testes. */
public record Pagina<T>(List<T> content, Metadados page) {

    public record Metadados(int size, int number, long totalElements, int totalPages) {}
}
