package com.stadyum.domain;

/** İş kuralı hatalarının makinece okunur kodları. HTTP karşılıkları api katmanında belirlenir. */
public enum ErrorCode {
    EVENT_NOT_FOUND,
    SEAT_NOT_FOUND,
    BLOCK_NOT_FOUND,
    BOOKING_NOT_FOUND,
    SEAT_UNAVAILABLE,
    HOLD_EXPIRED,
    MAX_SEATS_EXCEEDED,
    EMPTY_HOLD,
    SALES_CLOSED
}
