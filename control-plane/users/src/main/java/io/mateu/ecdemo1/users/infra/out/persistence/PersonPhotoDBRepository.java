package io.mateu.ecdemo1.users.infra.out.persistence;

import io.mateu.ecdemo1.users.application.out.PersonPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PersonPhotoDBRepository implements PersonPhotoRepository {

    final PersonPhotoEntityRepository entities;

    @Override
    public void save(Photo photo) {
        entities.save(new PersonPhotoEntity(photo.token(), photo.userId(), photo.contentType(), photo.content()));
    }

    @Override
    public Optional<Photo> findByToken(String token) {
        return entities.findById(token).map(e -> new Photo(e.getToken(), e.getUserId(), e.getContentType(), e.getContent()));
    }

    @Override
    public void deleteOfUser(String userId) {
        entities.deleteByUserId(userId);
    }
}
