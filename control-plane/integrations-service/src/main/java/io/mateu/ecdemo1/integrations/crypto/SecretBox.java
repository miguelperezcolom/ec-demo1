package io.mateu.ecdemo1.integrations.crypto;

import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The connection secrets at rest: AES-256-GCM under a key from a Secret, a fresh nonce each time.
 * With no key configured nothing is stored — a secret in clear in the database is worse than an
 * integration that cannot be saved.
 */
@Component
public class SecretBox {

    static final int NONCE = 12;
    static final int TAG_BITS = 128;

    final SecretKeySpec key;
    final SecureRandom random = new SecureRandom();

    public SecretBox(IntegrationsProperties properties) {
        var raw = properties.cryptoKey() == null || properties.cryptoKey().isBlank() ? null
                : Base64.getDecoder().decode(properties.cryptoKey().trim());
        if (raw != null && raw.length != 32) {
            throw new IllegalStateException("integrations.crypto-key must be 32 bytes, base64");
        }
        this.key = raw == null ? null : new SecretKeySpec(raw, "AES");
    }

    public String seal(String secret) {
        if (secret == null) {
            return null;
        }
        try {
            var nonce = new byte[NONCE];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            var sealed = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(NONCE + sealed.length).put(nonce).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot seal the secret", e);
        }
    }

    public String open(String sealed) {
        if (sealed == null) {
            return null;
        }
        try {
            var bytes = Base64.getDecoder().decode(sealed);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, bytes, 0, NONCE));
            return new String(cipher.doFinal(bytes, NONCE, bytes.length - NONCE), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot open the secret: was the key changed?", e);
        }
    }

    SecretKeySpec key() {
        if (key == null) {
            throw new IllegalStateException("No integrations.crypto-key: connection secrets cannot be stored or read");
        }
        return key;
    }
}
