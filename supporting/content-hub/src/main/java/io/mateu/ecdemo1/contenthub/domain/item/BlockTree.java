package io.mateu.ecdemo1.contenthub.domain.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * How a page's blocks nest. They stay one flat list, each naming the ROW or COLUMN that holds it
 * ({@link Block#parentId}), with every container right before what it holds. So whatever goes over a
 * page's blocks without caring how they are laid out — visibility, media references, content health,
 * translations, the sanitiser, version history — still reaches all of them, and whatever draws them one
 * after another (an e-mail) gets them stacked.
 */
public final class BlockTree {

    /** The grid a row divides its width into. */
    public static final int COLUMNS = 12;
    /** How many containers a block may be inside of. */
    public static final int MAX_DEPTH = 3;

    /** A block and what it holds. */
    public record Node(Block block, List<Node> children) {
    }

    private BlockTree() {
    }

    /**
     * The blocks with each container right before what it holds. A block whose parent is not there, is not a
     * container, is inside itself or is too deep goes to the top level: it stays on the page.
     */
    public static List<Block> normalized(List<Block> blocks) {
        if (blocks.stream().noneMatch(b -> b.parentId() != null)) {
            return blocks;
        }
        var out = new ArrayList<Block>();
        roots(blocks).forEach(n -> flatten(n, out));
        return out;
    }

    /** The blocks as a tree, in their order. */
    public static List<Node> of(List<Block> blocks) {
        return roots(blocks);
    }

    /** The blocks {@code visible} admits and whose containers it admits too: a hidden container hides what it holds. */
    public static List<Block> shown(List<Block> blocks, Predicate<Block> visible) {
        var hidden = new HashSet<String>();
        var out = new ArrayList<Block>();
        for (var b : normalized(blocks)) {
            if (hidden.contains(b.parentId()) || !visible.test(b)) {
                hidden.add(b.id());
            } else {
                out.add(b);
            }
        }
        return out;
    }

    /** The same blocks under other ids (a copy's), each still inside its container. */
    public static List<Block> withIds(List<Block> blocks, UnaryOperator<String> newId) {
        var ids = new HashMap<String, String>();
        blocks.forEach(b -> ids.put(b.id(), newId.apply(b.id())));
        return blocks.stream()
                .map(b -> b.withId(ids.get(b.id())).placedIn(b.parentId() == null ? null : ids.get(b.parentId()), b.span()))
                .toList();
    }

    /** What a child of a row with {@code siblings} children takes: its own span, or an equal share. */
    public static int spanOf(Block child, int siblings) {
        return child.span() != null ? child.span() : Math.max(1, COLUMNS / Math.max(1, siblings));
    }

    private static List<Node> roots(List<Block> blocks) {
        var byId = new HashMap<String, Block>();
        blocks.forEach(b -> byId.putIfAbsent(b.id(), b));
        var held = new LinkedHashMap<String, List<Block>>();
        var top = new ArrayList<Block>();
        for (var b : blocks) {
            if (b.parentId() != null && placed(b, byId)) {
                held.computeIfAbsent(b.parentId(), k -> new ArrayList<>()).add(b);
            } else {
                top.add(b.parentId() == null ? b : b.placedIn(null, b.span()));
            }
        }
        return top.stream().map(b -> node(b, held)).toList();
    }

    private static boolean placed(Block b, HashMap<String, Block> byId) {
        var depth = 0;
        var seen = new HashSet<String>();
        seen.add(b.id());
        for (var parent = b.parentId(); parent != null; parent = byId.get(parent).parentId()) {
            var holder = byId.get(parent);
            if (holder == null || !holder.type().isContainer() || !seen.add(parent) || ++depth > MAX_DEPTH) {
                return false;
            }
        }
        return true;
    }

    private static Node node(Block b, java.util.Map<String, List<Block>> held) {
        return new Node(b, held.getOrDefault(b.id(), List.of()).stream().map(c -> node(c, held)).toList());
    }

    private static void flatten(Node n, List<Block> out) {
        out.add(n.block());
        n.children().forEach(c -> flatten(c, out));
    }
}
