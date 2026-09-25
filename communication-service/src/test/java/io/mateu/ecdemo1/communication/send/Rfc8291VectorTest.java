package io.mateu.ecdemo1.communication.send;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static io.mateu.ecdemo1.communication.send.WebPushCrypto.b64;
import static io.mateu.ecdemo1.communication.send.WebPushCrypto.unb64;
import static org.assertj.core.api.Assertions.assertThat;

/** The worked example of RFC 8291, Appendix A: the same keys and salt give the same bytes. */
class Rfc8291VectorTest {

    @Test
    void theRfcsExampleEncryptsToTheRfcsBody() throws Exception {
        var asPublic = unb64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        var asPrivate = unb64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        var uaPublic = unb64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        var auth = unb64("BTBZMqHH6r4Tts7J_aSIgg");
        var salt = unb64("DGv6ra1nlYgDCS1FRnbzlw");
        var ephemeral = new KeyPair(WebPushCrypto.decodePublic(asPublic), WebPushCrypto.decodePrivate(asPrivate));

        var body = WebPushCrypto.encrypt("When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                uaPublic, auth, ephemeral, asPublic, salt);

        assertThat(b64(body)).isEqualTo("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }
}
