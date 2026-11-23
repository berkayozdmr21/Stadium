package com.perde.application.port;

import com.perde.application.view.SeatChange;

/** Koltuk durumu değişikliklerini canlı izleyenlere duyurur (işlem başarıyla bittikten sonra). */
public interface SeatChangePublisher {

    void publish(SeatChange change);
}
