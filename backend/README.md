# Sunucu (backend)

Java 21 · Spring Boot 4 · JPA/Hibernate · Flyway · WebSocket. Üst dizindeki [README](../README.md) projenin bütününü anlatır.

```powershell
./mvnw -DskipTests package
java -jar target/stadyum-backend-1.0.0.jar     # http://localhost:8080, bellek içi H2
./mvnw test                                    # 65 test
```

Varsayılan veritabanı bellek içi H2'dir (uygulama kapanınca veri silinir). PostgreSQL için `STADYUM_DB_URL`, `STADYUM_DB_USER`, `STADYUM_DB_PASSWORD` ortam değişkenleri verilir; şema Flyway ile kurulur.

## Katmanlar

```
domain          saf kurallar: stadyum düzeni, koltuk tutma kuralları (framework yok)
application     servisler: katalog, tutma, satın alma, süre dolumu
infrastructure  JPA, toplu koltuk üretimi, WebSocket yayını, zamanlanmış işler, demo kalabalığı
api             REST denetleyicileri ve RFC 9457 hata yanıtları
```

Bağımlılık yönü ArchUnit testleriyle denetlenir (`domain` kimseye, `application` yalnızca `domain`'e bağlıdır).

## Uç noktalar

| Yöntem | Yol | Açıklama |
|---|---|---|
| GET | `/api/stadium` | Stadyum düzeni: dış hat, katlar, 55 blok. Arayüz geometriyi buradan çizer |
| GET | `/api/events` | Maç listesi |
| GET | `/api/events/{slug}` | Maç, blok doluluğu, bölge fiyatları, ziyaretçinin tutması |
| GET | `/api/events/{slug}/blocks/{blok}` | Bir bloğun koltukları ve durumları |
| PUT/DELETE | `/api/events/{slug}/hold/seats/{id}` | Koltuk tut / bırak (`X-Hold-Token` başlığı) |
| DELETE | `/api/events/{slug}/hold` | Bütün seçimi bırak |
| POST | `/api/events/{slug}/hold/confirm` | Satın al (demo) |
| GET | `/api/bookings/{kod}` | Bilet |
| WS | `/ws/events/{slug}` | Canlı koltuk değişiklikleri ve izleyici sayısı |

## Önemli noktalar

- **Çift satış yok:** koltuk tutma koşullu `UPDATE ... WHERE status = 'AVAILABLE'` ile yapılır; aynı anda gelen iki istekten yalnızca biri satırı günceller. Tutma kayıtları `SELECT ... FOR UPDATE` ile kilitlenir.
- **Anahtar özetlenir:** ziyaretçinin tutma anahtarı veritabanında yalnızca SHA-256 özetiyle saklanır.
- **Zaman enjekte edilir:** `Clock` kullanıldığı için süre dolumu testlerde gerçek beklemeden denenir.
- **Toplu üretim:** her maç için ~42.500 koltuk JDBC toplu ekleme ile saniyeler içinde üretilir.
- **Yayın işlemden sonra:** koltuk değişiklikleri `AFTER_COMMIT` ile yayınlanır; geri alınan işlem hiçbir istemciye sızmaz.
- **H2 sürümü 2.3.232'ye sabitlidir.** H2 2.4.240'ta check kısıtı, onu kuran bağlantıya bağlı kalıyor; havuz o bağlantıyı yenileyince (varsayılan 30 dakika) her yazma `The database has been closed` ile patlıyordu. Ayrıntı `pom.xml`'deki yorumda.
- Paket adı `com.stadyum`; blok düzeni `domain/stadium/StadiumLayout` içindedir.
