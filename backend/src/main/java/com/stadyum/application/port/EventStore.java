package com.stadyum.application.port;

import com.stadyum.domain.model.Event;
import java.util.List;
import java.util.Optional;

public interface EventStore {

    List<Event> findAllOrderByStart();

    Optional<Event> findBySlug(String slug);

    Optional<Event> findById(Long id);
}
