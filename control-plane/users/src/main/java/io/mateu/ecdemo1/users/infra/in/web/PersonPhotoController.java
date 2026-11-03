package io.mateu.ecdemo1.users.infra.in.web;

import io.mateu.ecdemo1.users.application.out.PersonPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** A photo, by its unguessable address (PersonPhotos). Another photo is another address, so it can be cached for good. */
@RestController
@RequiredArgsConstructor
public class PersonPhotoController {

    final PersonPhotoRepository photos;

    @GetMapping("/_people/photos/{token}")
    public ResponseEntity<byte[]> photo(@PathVariable String token) {
        return photos.findByToken(token)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(p.content()))
                .orElse(ResponseEntity.notFound().build());
    }
}
