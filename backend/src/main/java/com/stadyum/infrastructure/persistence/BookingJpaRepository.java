package com.stadyum.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface BookingJpaRepository extends JpaRepository<BookingEntity, Long> {

    Optional<BookingEntity> findByCode(String code);
}
