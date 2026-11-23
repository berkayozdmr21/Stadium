package com.stadyum.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface EventJpaRepository extends JpaRepository<EventEntity, Long> {

    Optional<EventEntity> findBySlug(String slug);

    List<EventEntity> findAllByOrderByStartsAtAsc();
}
