package io.mateu.ecdemo1.contenthub.infra.in.render;

import io.mateu.ecdemo1.contenthub.application.out.RemoteBlockFallback;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A REMOTE block in a newsletter: RemoteUiRenderer's e-mail fallback, a link to the web version. */
@Component
@RequiredArgsConstructor
public class RemoteFallbacks implements RemoteBlockFallback {

    final RemoteUiRenderer remote;

    @Override
    public String html(Block block, String webUrl) {
        return remote.fallbackHtml(block, webUrl);
    }
}
