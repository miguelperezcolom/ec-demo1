package io.mateu.ecdemo1.iacp.application.out.crypto;

/**
 * Encrypts and decrypts the one secret this service holds: an LLM's API key.
 *
 * <p>A port, so the domain and the use cases never see an algorithm or a key. The implementation
 * is {@code infra/out/crypto/AesGcmSecretCipher}, and the two directions are deliberately not
 * symmetric in who may call them: everything writes, and exactly one collaborator reads.
 */
public interface SecretCipher {

    /** Plaintext in, storable ciphertext out. Never the reverse in anything the console reaches. */
    String encrypt(String plainText);

    /**
     * Only for serving an agent's configuration to a service that must authenticate with it.
     * Every other caller wants {@code credentialSet}, which is a boolean.
     */
    String decrypt(String cipherText);
}
