package com.stadyum.infrastructure.persistence;

import com.stadyum.domain.model.HoldStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

interface HoldJpaRepository extends JpaRepository<HoldEntity, Long> {

    Optional<HoldEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select h from HoldEntity h where h.tokenHash = :tokenHash")
    Optional<HoldEntity> lockByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select h from HoldEntity h where h.id = :id")
    Optional<HoldEntity> lockById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update HoldEntity h set h.status = :status where h.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") HoldStatus status);

    @Query("select h.id from HoldEntity h where h.status = :status and h.expiresAt <= :now order by h.expiresAt")
    List<Long> findDueIds(@Param("status") HoldStatus status, @Param("now") Instant now, Pageable page);
}
