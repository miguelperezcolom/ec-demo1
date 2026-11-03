package io.mateu.ecdemo1.users.application.out;

import java.util.Optional;

/** The employees' photos: stored apart from them, for the bytes not to travel with every user read. */
public interface PersonPhotoRepository {

    record Photo(String token, String userId, String contentType, byte[] content) {
    }

    void save(Photo photo);

    Optional<Photo> findByToken(String token);

    void deleteOfUser(String userId);
}
