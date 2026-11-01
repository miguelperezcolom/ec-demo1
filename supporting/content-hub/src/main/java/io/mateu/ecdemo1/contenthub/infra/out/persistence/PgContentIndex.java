package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.usecases.HtmlSanitizer;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.media.MediaReferences;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The projection and the consumers' reads, in PostgreSQL. Every read carries the same filter — the
 * profile's hash or TODOS among the subject's hashes ({@code hashes && array[hash, 'TODOS']}, a GIN
 * index), published, and between its publication and expiry at the instant asked — inside the query, so
 * paging, counts and search come out already filtered. No expression is ever translated to SQL.
 */
@Repository
@RequiredArgsConstructor
public class PgContentIndex implements ContentIndex {

    static final TypeReference<Map<Attribute, String>> ATTRIBUTES = new TypeReference<>() {
    };

    /** The one filter: who (hash), what state, when. Its three parameters: the hash array and the instant twice. */
    static final String VISIBLE = " d.hashes && ?::text[] and d.status = 'PUBLISHED' and d.publish_at <= ? "
            + "and (d.expire_at is null or d.expire_at > ?) ";

    /** The reader's language, or the default one when the item has no translation in theirs. */
    static final String LANGUAGE = " (d.language = ? or (d.is_default and not exists (select 1 from ch_item_doc o "
            + "where o.item_id = d.item_id and o.language = ?))) ";

    final JdbcTemplate jdbc;

    static String who(String hash) {
        return Sql.array(List.of(hash, EmployeeAttributes.EVERYONE_TOKEN));
    }

    // ── writing the projection ──────────────────────────────────────────────────────────────────

    @Override
    public void indexItem(Item item, Set<String> hashes, Map<String, Set<String>> blockHashes) {
        var version = item.latestVersion().orElseThrow();
        var content = version.content();
        var status = item.status() == ItemStatus.PUBLISHED ? "PUBLISHED" : item.status().name();
        jdbc.update("delete from ch_item_doc where item_id = ?", item.id());
        var audience = Json.write(content.audience());
        for (var e : content.translations().entrySet()) {
            var t = e.getValue();
            jdbc.update("insert into ch_item_doc (item_id, language, is_default, version, type, section_id, status, "
                            + "publish_at, expire_at, title, summary, body, url, author, published_at, read_tracking, "
                            + "confirmation_required, confirmation_deadline, confirmation_version, audience, hashes, tsv) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::text[], "
                            + "to_tsvector('simple', ?))",
                    item.id(), e.getKey(), e.getKey().equals(item.defaultLanguage()), version.number(),
                    item.type().name(), content.sectionId(), status, Sql.ts(content.publishAt()),
                    Sql.ts(content.expireAt()), t.title(), t.summary(), t.body(), content.url(), item.author(),
                    Sql.ts(version.publishedAt()), content.readTracking(), content.confirmationRequired(),
                    Sql.ts(content.confirmationDeadline()), item.confirmationVersion(), audience, Sql.array(hashes),
                    t.title() + " " + t.summary() + " " + HtmlSanitizer.text(t.body()));
        }
        jdbc.update("delete from ch_chunk where item_id = ?", item.id());
        for (var e : content.translations().entrySet()) {
            var t = e.getValue();
            var chunkNo = 0;
            for (var passage : Passages.of(t.title(), t.summary(), HtmlSanitizer.text(t.body()))) {
                jdbc.update("insert into ch_chunk (item_id, language, chunk_no, version, text) values (?, ?, ?, ?, ?)",
                        item.id(), e.getKey(), chunkNo++, version.number(), passage);
            }
        }
        jdbc.update("delete from ch_block_doc where item_id = ?", item.id());
        var position = 0;
        for (var block : content.blocks()) {
            jdbc.update("insert into ch_block_doc (item_id, block_id, position, audience, hashes) "
                            + "values (?, ?, ?, ?::jsonb, ?::text[])", item.id(), block.id(), position++,
                    Json.write(Audience.effective(content.audience(), block.audience())),
                    Sql.array(blockHashes.getOrDefault(block.id(), Set.of())));
        }
        jdbc.update("delete from ch_item_media where item_id = ?", item.id());
        for (var media : MediaReferences.in(content)) {
            jdbc.update("insert into ch_item_media (item_id, media_id) values (?, ?) on conflict do nothing",
                    item.id(), media);
        }
        jdbc.update("insert into ch_projection_state (subject, version, applied_at) values (?, ?, now()) "
                + "on conflict (subject) do update set version = excluded.version, applied_at = excluded.applied_at",
                "item:" + item.id(), version.number());
    }

    @Override
    public void indexSlot(Slot slot, Map<String, Set<String>> variantHashes) {
        jdbc.update("delete from ch_slot_variant_doc where slot_key = ?", slot.key());
        for (var v : slot.variants()) {
            jdbc.update("insert into ch_slot_variant_doc (slot_key, variant_id, priority, title, html, link_url, "
                            + "audience, hashes) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::text[])", slot.key(), v.id(),
                    v.priority(), v.title(), v.html(), v.linkUrl(), Json.write(v.audience()),
                    Sql.array(variantHashes.getOrDefault(v.id(), Set.of())));
        }
    }

    @Override
    public List<Subject> subjects() {
        var out = new ArrayList<Subject>();
        out.addAll(jdbc.query("select item_id, audience, hashes from ch_item_doc where is_default",
                (rs, i) -> new Subject(Kind.ITEM, rs.getString(1), Json.audience(rs.getString(2)), set(rs.getArray(3)))));
        out.addAll(jdbc.query("select item_id || '/' || block_id, audience, hashes from ch_block_doc",
                (rs, i) -> new Subject(Kind.BLOCK, rs.getString(1), Json.audience(rs.getString(2)), set(rs.getArray(3)))));
        out.addAll(jdbc.query("select slot_key || '/' || variant_id, audience, hashes from ch_slot_variant_doc",
                (rs, i) -> new Subject(Kind.VARIANT, rs.getString(1), Json.audience(rs.getString(2)), set(rs.getArray(3)))));
        return out;
    }

    @Override
    public void updateHashes(Kind kind, String key, Set<String> hashes) {
        var array = Sql.array(hashes);
        switch (kind) {
            case ITEM -> jdbc.update("update ch_item_doc set hashes = ?::text[] where item_id = ?", array, key);
            case BLOCK -> {
                var k = split(key);
                jdbc.update("update ch_block_doc set hashes = ?::text[] where item_id = ? and block_id = ?", array, k[0], k[1]);
            }
            case VARIANT -> {
                var k = split(key);
                jdbc.update("update ch_slot_variant_doc set hashes = ?::text[] where slot_key = ? and variant_id = ?",
                        array, k[0], k[1]);
            }
        }
    }

    @Override
    public void addHash(String hash, Collection<Subject> subjects) {
        Sql.array(List.of(hash));
        for (var s : subjects) {
            switch (s.kind()) {
                case ITEM -> jdbc.update("update ch_item_doc set hashes = array_append(hashes, ?) "
                        + "where item_id = ? and not (? = any(hashes))", hash, s.key(), hash);
                case BLOCK -> {
                    var k = split(s.key());
                    jdbc.update("update ch_block_doc set hashes = array_append(hashes, ?) where item_id = ? "
                            + "and block_id = ? and not (? = any(hashes))", hash, k[0], k[1], hash);
                }
                case VARIANT -> {
                    var k = split(s.key());
                    jdbc.update("update ch_slot_variant_doc set hashes = array_append(hashes, ?) where slot_key = ? "
                            + "and variant_id = ? and not (? = any(hashes))", hash, k[0], k[1], hash);
                }
            }
        }
    }

    @Override
    public void removeHash(String hash) {
        for (var table : List.of("ch_item_doc", "ch_block_doc", "ch_slot_variant_doc")) {
            jdbc.update("update " + table + " set hashes = array_remove(hashes, ?) where ? = any(hashes)", hash, hash);
        }
    }

    static String[] split(String key) {
        var at = key.lastIndexOf('/');
        return new String[]{key.substring(0, at), key.substring(at + 1)};
    }

    static Set<String> set(Array array) throws SQLException {
        return array == null ? Set.of() : new LinkedHashSet<>(Arrays.asList((String[]) array.getArray()));
    }

    // ── the universe of profiles ───────────────────────────────────────────────────────────────

    @Override
    public Map<String, EmployeeAttributes> profiles() {
        var out = new LinkedHashMap<String, EmployeeAttributes>();
        jdbc.query("select hash, attributes from ch_profile order by hash", rs -> {
            out.put(rs.getString(1), new EmployeeAttributes(Json.read(rs.getString(2), ATTRIBUTES), null));
        });
        return out;
    }

    @Override
    public boolean knowsProfile(String hash) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from ch_profile where hash = ?)",
                Boolean.class, hash));
    }

    @Override
    public void registerProfile(EmployeeAttributes attributes) {
        jdbc.update("insert into ch_profile (hash, attributes, canonical, first_seen) values (?, ?::jsonb, ?, now()) "
                + "on conflict do nothing", attributes.hash(), Json.write(attributes.values()), attributes.canonical());
    }

    @Override
    public void forgetProfile(String hash) {
        jdbc.update("delete from ch_profile where hash = ?", hash);
    }

    @Override
    public Optional<Applied> applied(String itemId) {
        return jdbc.query("select version, applied_at from ch_projection_state where subject = ?",
                (rs, i) -> new Applied(rs.getInt(1), Sql.instant(rs.getTimestamp(2))), "item:" + itemId).stream().findFirst();
    }

    // ── the consumers' reads ────────────────────────────────────────────────────────────────────

    @Override
    public Page<VisibleItem> visibleItems(String hash, String language, Instant now, ContentQuery query) {
        var where = new StringBuilder(" where " + VISIBLE + " and " + LANGUAGE);
        var params = new ArrayList<Object>(List.of(who(hash), Sql.ts(now), Sql.ts(now), language, language));
        if (!query.types().isEmpty()) {
            where.append(" and d.type in (")
                    .append(query.types().stream().map(t -> "?").collect(Collectors.joining(","))).append(") ");
            query.types().forEach(t -> params.add(t.name()));
        }
        if (query.sectionId() != null) {
            where.append(" and d.section_id = ? ");
            params.add(query.sectionId());
        }
        if (query.itemIds() != null) {
            if (query.itemIds().isEmpty()) {
                return new Page<>(List.of(), 0);
            }
            where.append(" and d.item_id in (")
                    .append(query.itemIds().stream().map(t -> "?").collect(Collectors.joining(","))).append(") ");
            params.addAll(query.itemIds());
        }
        var tsQuery = tsQuery(query.text());
        if (tsQuery != null) {
            where.append(" and d.tsv @@ to_tsquery('simple', ?) ");
            params.add(tsQuery);
        }
        var total = jdbc.queryForObject("select count(*) from ch_item_doc d" + where, Long.class, params.toArray());
        var order = tsQuery != null ? " order by ts_rank(d.tsv, to_tsquery('simple', ?)) desc, d.publish_at desc "
                : " order by d.publish_at desc, d.item_id ";
        var pageParams = new ArrayList<>(params);
        if (tsQuery != null) {
            pageParams.add(tsQuery);
        }
        pageParams.add(query.offset());
        pageParams.add(query.limit());
        var rows = jdbc.query("select d.item_id, d.version, d.type, d.section_id, d.language, d.title, d.summary, "
                        + "d.body, d.url, d.author, d.publish_at, d.expire_at, d.read_tracking, d.confirmation_required, "
                        + "d.confirmation_deadline, d.confirmation_version from ch_item_doc d" + where + order
                        + " offset ? limit ?",
                (rs, i) -> visible(rs), pageParams.toArray());
        return new Page<>(rows, total == null ? 0 : total);
    }

    /** Free text as a prefix query over words: "polí vacac" → "polí:* & vacac:*". Null when there is none. */
    static String tsQuery(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        var words = Arrays.stream(text.toLowerCase().split("[^\\p{L}\\p{N}]+")).filter(w -> !w.isBlank())
                .map(w -> w + ":*").toList();
        return words.isEmpty() ? null : String.join(" & ", words);
    }

    static VisibleItem visible(ResultSet rs) throws SQLException {
        return new VisibleItem(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
                Sql.instant(rs.getTimestamp(11)), Sql.instant(rs.getTimestamp(12)), rs.getBoolean(13),
                rs.getBoolean(14), Sql.instant(rs.getTimestamp(15)), rs.getInt(16));
    }

    @Override
    public boolean isVisible(String itemId, String hash, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from ch_item_doc d where d.item_id = ? and"
                + VISIBLE + ")", Boolean.class, itemId, who(hash), Sql.ts(now), Sql.ts(now)));
    }

    @Override
    public Set<String> sectionsWithVisibleContent(String hash, Instant now) {
        return new LinkedHashSet<>(jdbc.queryForList("select distinct d.section_id from ch_item_doc d where"
                + VISIBLE + " and d.section_id is not null", String.class, who(hash), Sql.ts(now), Sql.ts(now)));
    }

    @Override
    public List<String> visibleBlockIds(String itemId, String hash) {
        return jdbc.queryForList("select d.block_id from ch_block_doc d where d.item_id = ? and d.hashes && ?::text[] "
                + "order by d.position", String.class, itemId, who(hash));
    }

    @Override
    public Optional<VisibleVariant> topVisibleVariant(String slotKey, String hash) {
        return jdbc.query("select d.slot_key, d.variant_id, d.priority, d.title, d.html, d.link_url from "
                        + "ch_slot_variant_doc d where d.slot_key = ? and d.hashes && ?::text[] "
                        + "order by d.priority desc limit 1",
                (rs, i) -> new VisibleVariant(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getString(6)), slotKey, who(hash)).stream().findFirst();
    }

    @Override
    public List<Passage> passagesToEmbed(int limit) {
        return jdbc.query("select c.item_id, c.language, c.chunk_no, '', c.text from ch_chunk c where c.embedding is null "
                        + "order by c.item_id, c.language, c.chunk_no limit ?",
                (rs, i) -> new Passage(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5), 0),
                limit);
    }

    @Override
    public void saveEmbedding(Passage p, float[] embedding) {
        jdbc.update("update ch_chunk set embedding = ?::vector where item_id = ? and language = ? and chunk_no = ?",
                vector(embedding), p.itemId(), p.language(), p.chunkNo());
    }

    @Override
    public List<Passage> similarVisiblePassages(String hash, String language, Instant now, float[] question, int limit) {
        var q = vector(question);
        return jdbc.query("select c.item_id, c.language, c.chunk_no, d.title, c.text, c.embedding <=> ?::vector as dist "
                        + "from ch_chunk c join ch_item_doc d on d.item_id = c.item_id and d.language = c.language "
                        + "where c.embedding is not null and" + VISIBLE + " and " + LANGUAGE
                        + "order by c.embedding <=> ?::vector limit ?",
                (rs, i) -> new Passage(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                        rs.getDouble(6)),
                q, who(hash), Sql.ts(now), Sql.ts(now), language, language, q, limit);
    }

    static String vector(float[] v) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    @Override
    public boolean isMediaVisible(String mediaId, String hash, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from ch_item_media m join ch_item_doc d "
                + "on d.item_id = m.item_id where m.media_id = ? and" + VISIBLE + ")", Boolean.class, mediaId, who(hash),
                Sql.ts(now), Sql.ts(now)));
    }
}
