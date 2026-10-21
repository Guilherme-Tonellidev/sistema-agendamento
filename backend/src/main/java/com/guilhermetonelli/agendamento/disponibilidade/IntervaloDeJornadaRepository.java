package com.guilhermetonelli.agendamento.disponibilidade;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface IntervaloDeJornadaRepository extends JpaRepository<IntervaloDeJornada, UUID> {

    List<IntervaloDeJornada> findByProfissionalIdOrderByDiaSemanaAscInicioAsc(UUID profissionalId);

    @Modifying
    @Query("delete from IntervaloDeJornada i where i.profissionalId = :profissionalId")
    void removerDoProfissional(UUID profissionalId);
}
