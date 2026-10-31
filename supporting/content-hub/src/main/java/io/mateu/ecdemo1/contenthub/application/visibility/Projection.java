package io.mateu.ecdemo1.contenthub.application.visibility;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps the projection {@code subject → profile hashes that see it} in step: the visibility policy, and
 * nothing else, evaluates the expressions here, over the universe of profiles that comes from the
 * directory. Every consumer then filters by this projection inside the store.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Projection {

    final ContentIndex index;
    final ItemRepository items;
    final SlotRepository slots;
    final EmployeeDirectory directory;
    final NamedAudiences namedAudiences;
    final ProjectionCache cache;

    /** An item was published or retired: its documents and its blocks, from its latest version. */
    @Transactional
    public void projectItem(String itemId) {
        var item = items.find(itemId).orElse(null);
        if (item == null || item.latestVersion().isEmpty()) {
            return;
        }
        var policy = namedAudiences.policy();
        var universe = index.profiles().values();
        var content = item.latestVersion().get().content();
        var hashes = policy.hashesSeeing(content.audience(), universe);
        var blockHashes = new LinkedHashMap<String, Set<String>>();
        for (var block : content.blocks()) {
            blockHashes.put(block.id(), policy.hashesSeeing(Audience.effective(content.audience(), block.audience()),
                    universe));
        }
        index.indexItem(item, hashes, blockHashes);
        cache.invalidateAll();
    }

    @Transactional
    public void projectSlot(String slotKey) {
        var slot = slots.find(slotKey).orElse(null);
        if (slot == null) {
            return;
        }
        var policy = namedAudiences.policy();
        var universe = index.profiles().values();
        var variantHashes = new LinkedHashMap<String, Set<String>>();
        for (var v : slot.variants()) {
            variantHashes.put(v.id(), policy.hashesSeeing(v.audience(), universe));
        }
        index.indexSlot(slot, variantHashes);
        cache.invalidateAll();
    }

    /**
     * Recalculates every subject's hashes from its stored audience — after a named audience is redefined,
     * and whenever someone wants to check the projection from scratch. Millions of evaluations at real
     * scale: seconds.
     */
    @Transactional
    public int rebuild() {
        var policy = namedAudiences.policy();
        var universe = index.profiles().values();
        var changed = 0;
        for (var subject : index.subjects()) {
            var hashes = policy.hashesSeeing(subject.audience(), universe);
            if (!hashes.equals(subject.hashes())) {
                index.updateHashes(subject.kind(), subject.key(), hashes);
                changed++;
            }
        }
        cache.invalidateAll();
        log.info("Projection rebuilt: {} subjects changed", changed);
        return changed;
    }

    /**
     * A profile not seen before — a new employee, someone whose attributes changed, or a request from a
     * hash not materialised yet: its hash is added to every subject that admits it, synchronously. One
     * hash against all subjects is milliseconds; the request that needed it then queries as always.
     *
     * @return whether it was new
     */
    @Transactional
    public boolean materialise(EmployeeAttributes profile) {
        if (index.knowsProfile(profile.hash())) {
            return false;
        }
        index.registerProfile(profile);
        var policy = namedAudiences.policy();
        var admitting = new ArrayList<ContentIndex.Subject>();
        for (var subject : index.subjects()) {
            if (!VisibilityPolicy.seen(subject.hashes(), EmployeeAttributes.EVERYONE_TOKEN)
                    && policy.admits(subject.audience(), profile)) {
                admitting.add(subject);
            }
        }
        index.addHash(profile.hash(), admitting);
        log.info("Profile {} materialised: sees {} restricted subjects", profile.hash(), admitting.size());
        return true;
    }

    /** Profiles no enabled employee has any more: out of the projection and the universe. */
    @Transactional
    public int forgetOrphanProfiles() {
        var inUse = directory.all().stream().filter(Employee::enabled).map(Employee::hash).collect(Collectors.toSet());
        var orphans = new HashSet<>(index.profiles().keySet());
        orphans.removeAll(inUse);
        for (var hash : orphans) {
            index.removeHash(hash);
            index.forgetProfile(hash);
            cache.invalidateHash(hash);
        }
        if (!orphans.isEmpty()) {
            log.info("Forgot {} orphan profiles", orphans.size());
        }
        return orphans.size();
    }
}
