package com.stadyum.application.port;

/** Çalışma anı ayarlarından arayüzün bilmesi gerekenler. */
public interface RuntimeInfo {

    /** Sahte ziyaretçiler etkinse arayüz bunu açıkça belirtir. */
    boolean demoCrowdEnabled();
}
