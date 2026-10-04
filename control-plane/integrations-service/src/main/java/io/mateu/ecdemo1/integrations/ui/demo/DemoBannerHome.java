package io.mateu.ecdemo1.integrations.ui.demo;

import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import org.springframework.stereotype.Service;

/**
 * The base the consoles' headers ask for the demo's banner from: UserWidget mounts it as baseUrl
 * {@code /_demo-banner}, route {@code /banner}, and that request lands on this UI's endpoint, which
 * resolves {@code /banner} to {@link DemoBanner} — the same arrangement as the inbox's badge under
 * {@code /_inbox}. Without a UI here there is no {@code /_demo-banner/mateu/**} endpoint at all, and
 * every header showed «Not found» where the banner goes. Nothing else lives under it: the Demo page is
 * {@code /_demo}, the control host's alone.
 */
@UI("/_demo-banner")
@Title("")
@Service
public class DemoBannerHome {
}
