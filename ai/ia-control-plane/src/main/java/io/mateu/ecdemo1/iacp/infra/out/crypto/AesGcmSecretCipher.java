package io.mateu.ecdemo1.iacp.infra.out.crypto;

import io.mateu.ecdemo1.iacp.application.out.crypto.SecretCipher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM over the LLM credentials, with the key supplied as an environment variable and held
 * nowhere else.
 *
 * <p>GCM rather than CBC because it authenticates as well as encrypts: ciphertext that has been
 * edited in the database fails to decrypt instead of decrypting to something else. A fresh random
 * 12-byte IV per encryption, prefixed to the ciphertext, because reusing an IV under one key is
 * the way GCM fails catastrophically rather than gracefully.
 *
 * <p><strong>What this does and does not protect.</strong> It protects a database dump, a stolen
 * volume snapshot, a backup, and anyone with read access to the table. It does not protect against
 * someone who has both the database and {@code CP_CRYPTO_KEY} — which includes anyone who can exec
 * into this pod. That is the honest boundary, and it is the reason the console can replace a
 * credential but never read one back: the threat this design actually addresses is a key leaking
 * through a screen, a listing, a log line or a backup, not a root shell.
 *
 * <p>Rotating {@code CP_CRYPTO_KEY} makes every stored credential undecryptable — there is no
 * re-wrapping here. Rotation means entering the keys again.
 */
@Component
@Slf4j
public class AesGcmSecretCipher implements SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;
    private final boolean configured;

    public AesGcmSecretCipher(@Value("${cp.crypto.key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            this.configured = false;
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("CP_CRYPTO_KEY is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "CP_CRYPTO_KEY must decode to 32 bytes for AES-256, got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
        this.configured = true;
    }

    @PostConstruct
    void warnIfUnconfigured() {
        if (!configured) {
            // Started, so the catalogues are usable and the console comes up. Only the credential
            // field is dead, and it says so rather than storing a key in the clear — which is the
            // one outcome that must not be reachable by forgetting a variable.
            log.error("CP_CRYPTO_KEY is not set. LLM credentials cannot be stored or read, and "
                    + "every other part of this service works. Set it from the ec-cp-crypto secret.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String encrypt(String plainText) {
        requireConfigured();
        try {
            var iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            var cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            var out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // Deliberately not chaining the cause into the message: a JCE exception can carry
            // fragments of what it was given.
            throw new IllegalStateException("Could not encrypt the credential");
        }
    }

    @Override
    public String decrypt(String cipherText) {
        requireConfigured();
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        try {
            var all = Base64.getDecoder().decode(cipherText);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt the credential. Either it was "
                    + "written under a different CP_CRYPTO_KEY, or the stored value has been altered.");
        }
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException(
                    "CP_CRYPTO_KEY is not set, so credentials cannot be stored or read.");
        }
    }
}
