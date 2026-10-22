package com.guilhermetonelli.agendamento.seguranca;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByProfissionalId(UUID profissionalId);

    boolean existsByEmail(String email);
}
