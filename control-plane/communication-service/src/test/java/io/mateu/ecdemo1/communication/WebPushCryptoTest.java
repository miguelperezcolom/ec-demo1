package io.mateu.ecdemo1.communication;

import io.mateu.ecdemo1.communication.send.WebPushCrypto;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** What a browser does with a push: derive the key from its own private key and decrypt it (RFC 8291). */
class WebPushCryptoTest {

    @Test
    void theBrowserTheSubscriptionIsForDecryptsThePayload() throws Exception {
        var browser = WebPushCrypto.newKeyPair();
        var uaPublic = WebPushCrypto.encode((ECPublicKey) browser.getPublic());
        var auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        var payload = "{\"title\":\"MRU01 · Mapeado pendiente\",\"url\":\"https://console.ec1.mateu.io/mapping/pending\"}"
                .getBytes(StandardCharsets.UTF_8);

        var body = WebPushCrypto.encrypt(payload, uaPublic, auth);

        var plain = decrypt(body, browser, auth);

        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2);
        assertThat(Arrays.copyOf(plain, plain.length - 1)).isEqualTo(payload);
    }

    @Test
    void theVapidTokenIsSignedForThePushServicesOriginAndVerifiesWithOurPublicKey() throws Exception {
        var vapid = WebPushCrypto.newKeyPair();
        var publicKey = WebPushCrypto.b64(WebPushCrypto.encode((ECPublicKey) vapid.getPublic()));
        var privateKey = WebPushCrypto.b64(WebPushCrypto.encode((ECPrivateKey) vapid.getPrivate()));

        var header = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com/fcm/send/abc:def", "mailto:ops@example.com",
                publicKey, privateKey, 1_900_000_000L);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + publicKey);
        var jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        var parts = jwt.split("\\.");
        assertThat(new String(WebPushCrypto.unb64(parts[1]), StandardCharsets.UTF_8))
                .contains("\"aud\":\"https://fcm.googleapis.com\"", "\"exp\":1900000000", "\"sub\":\"mailto:ops@example.com\"");
        var verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(vapid.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(WebPushCrypto.unb64(parts[2]))).isTrue();
    }

    /** What the browser does: its private key and the auth secret recover the record (with its 0x02 delimiter). */
    static byte[] decrypt(byte[] body, java.security.KeyPair browser, byte[] auth) throws Exception {
        var uaPublic = WebPushCrypto.encode((ECPublicKey) browser.getPublic());
        var in = ByteBuffer.wrap(body);
        var salt = new byte[16];
        in.get(salt);
        assertThat(in.getInt()).isEqualTo(4096);
        var asPublic = new byte[in.get()];
        in.get(asPublic);
        var ciphertext = new byte[in.remaining()];
        in.get(ciphertext);

        var agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(publicKey(asPublic, (ECPublicKey) browser.getPublic()), true);
        var ecdh = agreement.generateSecret();
        var prkKey = hmac(auth, ecdh);
        var ikm = hmac(prkKey, cat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic, new byte[]{1}));
        var prk = hmac(salt, ikm);
        var cek = Arrays.copyOf(hmac(prk, cat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 16);
        var nonce = Arrays.copyOf(hmac(prk, cat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 12);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        return cipher.doFinal(ciphertext);
    }

    static java.security.PublicKey publicKey(byte[] raw, ECPublicKey sameCurve) throws Exception {
        var point = new ECPoint(new java.math.BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                new java.math.BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, sameCurve.getParams()));
    }

    static byte[] hmac(byte[] key, byte[] data) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] cat(byte[]... parts) {
        var out = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(p -> p.length).sum());
        for (var p : parts) out.put(p);
        return out.array();
    }
}
