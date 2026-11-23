package com.stadyum.application.port;

import com.stadyum.application.view.SeatChange;

/** Koltuk durumu değişikliklerini canlı izleyenlere duyurur (işlem başarıyla bittikten sonra). */
public interface SeatChangePublisher {

    void publish(SeatChange change);
}
