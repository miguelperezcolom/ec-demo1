package io.mateu.ecdemo1.operamock.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.*;
import org.springframework.stereotype.Service;

/** What reached "Opera": the double's own screens. */
@UI("/_opera-mock")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Opera (double)")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class OperaMockHome {

    /** One section, so the shell's RemoteMenu can name it: "Opera". */
    @Menu
    OperaMenu opera;
}
