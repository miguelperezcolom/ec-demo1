package io.mateu.ecdemo1.frontoffice.domain.automation;

import org.springframework.data.relational.core.mapping.Table;

/** An external system the automation talks to (OHIP, Voxel, CRS…). Value object. */
@Table("automation_system")
public record ConnectedSystem(String name) {}
