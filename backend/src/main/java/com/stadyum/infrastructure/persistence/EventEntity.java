package com.stadyum.infrastructure.persistence;

import com.stadyum.domain.model.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "events")
class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 160)
    private String venue;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private int hue;

    protected EventEntity() {
    }

    static EventEntity from(Event e) {
        EventEntity entity = new EventEntity();
        entity.id = e.id();
        entity.slug = e.slug();
        entity.title = e.title();
        entity.category = e.category();
        entity.venue = e.venue();
        entity.city = e.city();
        entity.startsAt = e.startsAt();
        entity.description = e.description();
        entity.hue = e.hue();
        return entity;
    }

    Event toDomain() {
        return new Event(id, slug, title, category, venue, city, startsAt, description, hue);
    }

    Long getId() {
        return id;
    }
}
