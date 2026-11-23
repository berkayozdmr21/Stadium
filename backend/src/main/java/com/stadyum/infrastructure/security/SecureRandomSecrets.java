package com.stadyum.infrastructure.security;

import com.stadyum.application.port.Secrets;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
class SecureRandomSecrets implements Secrets {

    /** Karıştırılabilen karakterler (0/O, 1/I/L) yok: bilet kodu elle de yazılabilsin. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String newHoldToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 her JVM'de bulunmalı", e);
        }
    }

    @Override
    public String newBookingCode() {
        StringBuilder code = new StringBuilder("PRD-");
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }
}
