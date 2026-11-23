package com.perde.domain;

/** Bir iş kuralı çiğnendiğinde fırlatılır. Mesaj kullanıcıya gösterilebilir. */
public class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static DomainException eventNotFound() {
        return new DomainException(ErrorCode.EVENT_NOT_FOUND, "Etkinlik bulunamadı.");
    }

    public static DomainException seatNotFound() {
        return new DomainException(ErrorCode.SEAT_NOT_FOUND, "Koltuk bulunamadı.");
    }

    public static DomainException bookingNotFound() {
        return new DomainException(ErrorCode.BOOKING_NOT_FOUND, "Bu kodla bir bilet bulunamadı.");
    }

    public static DomainException seatUnavailable() {
        return new DomainException(ErrorCode.SEAT_UNAVAILABLE, "Bu koltuk az önce başkası tarafından alındı.");
    }

    public static DomainException holdExpired() {
        return new DomainException(ErrorCode.HOLD_EXPIRED, "Koltuk tutma süren doldu. Koltukları yeniden seçmelisin.");
    }

    public static DomainException maxSeatsExceeded(int max) {
        return new DomainException(ErrorCode.MAX_SEATS_EXCEEDED, "Tek seferde en fazla " + max + " koltuk seçebilirsin.");
    }

    public static DomainException emptyHold() {
        return new DomainException(ErrorCode.EMPTY_HOLD, "Satın almak için en az bir koltuk seçmelisin.");
    }

    public static DomainException salesClosed() {
        return new DomainException(ErrorCode.SALES_CLOSED, "Bu etkinlik için bilet satışı kapandı.");
    }
}
