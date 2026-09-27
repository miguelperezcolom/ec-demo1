package io.mateu.ecdemo1.frontoffice.domain.automation;


/** An external system the automation talks to (OHIP, Voxel, CRS…). Value object. */
public record ConnectedSystem(String name) {}
