package io.mateu.ecdemo1.iacp.application.out.gitops;

/**
 * Where the desired catalogue is read from — a GitHub repo in this deployment, but the port names
 * none of that.
 *
 * <p><strong>It fetches all-or-nothing, and throws rather than return a partial answer.</strong>
 * That contract is what makes deletion safe: the reconciler deletes git-managed entries that are
 * absent from the desired state, so a fetch that half-failed and returned an empty or truncated
 * catalogue would read as "everything was removed from the repo" and delete it all. Throwing on any
 * failure means a bad fetch leaves the live catalogues exactly as they are.
 */
public interface CatalogueSource {

    /**
     * @return every entry the repo declares, parsed and sorted by kind
     * @throws RuntimeException if the source could not be read in full. The reconciler treats this
     *         as "no information", not "nothing declared", and changes nothing.
     */
    DesiredCatalogue fetch();
}
