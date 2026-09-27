package io.mateu.ecdemo1.frontoffice.domain.stay;

/**
 * Where the per-stay {@link CheckInOps} flags are kept. A stay nobody worked on yet has none of its
 * operations done.
 */
public interface CheckInOpsRepository {

  CheckInOps of(String stayId);

  CheckInOps save(String stayId, CheckInOps ops);
}
