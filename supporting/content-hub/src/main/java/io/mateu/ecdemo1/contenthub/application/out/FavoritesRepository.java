package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.favorites.Favorites;

public interface FavoritesRepository {

    /** The employee's favourites; an empty list when they have none yet. */
    Favorites get(String userId);

    void save(Favorites favorites);
}
