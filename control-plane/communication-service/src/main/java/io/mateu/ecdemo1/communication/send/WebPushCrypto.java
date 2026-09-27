package io.mateu.ecdemo1.communication.send;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Web Push with nothing but the JDK: the payload encrypted for the browser's subscription (RFC 8291,
 * {@code aes128gcm} of RFC 8188), and the VAPID token that tells the push service who sends it
 * (RFC 8292). P-256 keys throughout, as the browsers and the push services require.
 */
public final class WebPushCrypto {

    static final SecureRandom RANDOM = new SecureRandom();
    static final ECParameterSpec P256 = p256();
    static final int RECORD_SIZE = 4096;

    private WebPushCrypto() {
    }

    /**
     * The request body for a subscription: salt, record size, the sender's ephemeral public key, and
     * the payload encrypted with a key only that browser can derive.
     *
     * @param uaPublic the subscription's {@code p256dh}: the browser's public key, uncompressed
     * @param auth     the subscription's {@code auth} secret
     */
    public static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] auth) throws GeneralSecurityException {
        var ephemeral = newKeyPair();
        var asPublic = encode((ECPublicKey) ephemeral.getPublic());
        var salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(payload, uaPublic, auth, ephemeral, asPublic, salt);
    }

    static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] auth, KeyPair ephemeral, byte[] asPublic, byte[] salt)
            throws GeneralSecurityException {
        var agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(decodePublic(uaPublic), true);
        var ecdhSecret = agreement.generateSecret();

        var prkKey = hmac(auth, ecdhSecret);
        var keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic);
        var ikm = hmac(prkKey, concat(keyInfo, new byte[]{1}));
        var prk = hmac(salt, ikm);
        var cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 16);
        var nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 12);

        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // One record, the last: the payload and the 0x02 delimiter, no padding.
        var ciphertext = cipher.doFinal(concat(payload, new byte[]{2}));

        return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + ciphertext.length)
                .put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).put(ciphertext)
                .array();
    }

    /** The {@code Authorization} header value: a VAPID JWT for the push service's origin, and our key. */
    public static String vapidAuthorization(String endpoint, String subject, String publicKey, String privateKey,
                                            long expiresAtEpochSecond) throws GeneralSecurityException {
        var uri = java.net.URI.create(endpoint);
        var audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        var header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        var claims = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresAtEpochSecond + ",\"sub\":\"" + subject + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        var signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(decodePrivate(unb64(privateKey)));
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return "vapid t=" + header + "." + claims + "." + b64(signer.sign()) + ", k=" + publicKey;
    }

    public static KeyPair newKeyPair() throws GeneralSecurityException {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** A public key as the browsers and VAPID want it: 0x04, then X and Y, 32 bytes each. */
    public static byte[] encode(ECPublicKey key) {
        return concat(new byte[]{4}, fixed(key.getW().getAffineX()), fixed(key.getW().getAffineY()));
    }

    public static byte[] encode(ECPrivateKey key) {
        return fixed(key.getS());
    }

    static ECPublicKey decodePublic(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4) {
            throw new GeneralSecurityException("Not an uncompressed P-256 public key");
        }
        var point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    static ECPrivateKey decodePrivate(byte[] raw) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] fixed(BigInteger value) {
        var bytes = value.toByteArray();
        if (bytes.length == 32) return bytes;
        var out = new byte[32];
        if (bytes.length > 32) {
            System.arraycopy(bytes, bytes.length - 32, out, 0, 32);
        } else {
            System.arraycopy(bytes, 0, out, 32 - bytes.length, bytes.length);
        }
        return out;
    }

    static byte[] concat(byte[]... parts) {
        var length = 0;
        for (var p : parts) length += p.length;
        var out = ByteBuffer.allocate(length);
        for (var p : parts) out.put(p);
        return out.array();
    }

    public static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] unb64(String text) {
        return Base64.getUrlDecoder().decode(text.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    private static ECParameterSpec p256() {
        try {
            return ((ECPublicKey) newKeyPair().getPublic()).getParams();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No P-256 in this JDK", e);
        }
    }
}
