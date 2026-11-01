package io.mateu.ecdemo1.contenthub.application.out;

import java.util.List;

/**
 * Which pages are homes, in order: a reader's home is the first of them they can see (and the
 * portal's default home if none). The pages themselves are ordinary items with their own audience.
 */
public interface HomePages {

    List<String> pageIds();

    void save(List<String> pageIds);
}
