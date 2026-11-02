package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.item.Block;

/**
 * What a block showing another UI (REMOTE) becomes where that UI cannot run — a newsletter's email: a link to
 * the page on the web, where it does. The adapter is the remote UIs' own renderer (RemoteUiRenderer).
 */
public interface RemoteBlockFallback {

    String html(Block block, String webUrl);
}
