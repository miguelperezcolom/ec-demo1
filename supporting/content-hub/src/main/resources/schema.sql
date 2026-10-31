-- content-hub's own tables. Additive only: "if not exists" everywhere, nothing dropped or renamed.

-- The aggregates, each as a JSON document (with the columns its listings sort and search by).
create table if not exists ch_item (
    id text primary key, doc jsonb not null, type text, status text, title text, author text,
    updated_at timestamptz, pending_changes boolean, in_review boolean);
create table if not exists ch_navigation (id text primary key, doc jsonb not null);
create table if not exists ch_slot (key text primary key, doc jsonb not null);
create table if not exists ch_named_audience (id text primary key, name text, doc jsonb not null);
create table if not exists ch_newsletter (
    id text primary key, doc jsonb not null, status text, scheduled_at timestamptz);
create table if not exists ch_favorites (user_id text primary key, doc jsonb not null);
create table if not exists ch_ack (
    item_id text not null, version int not null, user_id text not null, type text not null,
    at timestamptz not null, primary key (item_id, version, user_id, type));
create table if not exists ch_media (
    id text primary key, kind text not null, title text, file_name text, content_type text, size bigint,
    external_url text, uploaded_by text, uploaded_at timestamptz, bytes bytea);

-- The employee directory (fed by the users service) and the universe of profiles.
create table if not exists ch_employee (
    user_id text primary key, name text, email text, enabled boolean, attributes jsonb, language text,
    hash text, updated_at timestamptz);
create table if not exists ch_profile (hash text primary key, attributes jsonb not null, canonical text,
    first_seen timestamptz);

-- The read side: published content with the hashes that see it. Every consumer filters here.
create table if not exists ch_item_doc (
    item_id text not null, language text not null, is_default boolean not null, version int not null,
    type text, section_id text, status text not null, publish_at timestamptz not null, expire_at timestamptz,
    title text, summary text, body text, url text, author text, published_at timestamptz,
    read_tracking boolean, confirmation_required boolean, confirmation_deadline timestamptz,
    confirmation_version int, audience jsonb not null, hashes text[] not null, tsv tsvector,
    primary key (item_id, language));
create index if not exists ch_item_doc_hashes on ch_item_doc using gin (hashes);
create index if not exists ch_item_doc_tsv on ch_item_doc using gin (tsv);
create index if not exists ch_item_doc_publish on ch_item_doc (publish_at desc);
create table if not exists ch_block_doc (
    item_id text not null, block_id text not null, position int not null, audience jsonb not null,
    hashes text[] not null, primary key (item_id, block_id));
create index if not exists ch_block_doc_hashes on ch_block_doc using gin (hashes);
create table if not exists ch_slot_variant_doc (
    slot_key text not null, variant_id text not null, priority int not null, title text, html text,
    link_url text, audience jsonb not null, hashes text[] not null, primary key (slot_key, variant_id));
create index if not exists ch_slot_variant_doc_hashes on ch_slot_variant_doc using gin (hashes);
create table if not exists ch_item_media (item_id text not null, media_id text not null,
    primary key (item_id, media_id));
create table if not exists ch_projection_state (subject text primary key, version int, applied_at timestamptz);

-- The RAG: each published translation cut into passages, embedded (multilingual-e5-small, 384
-- dimensions) by a job; searched by similarity under the same visibility filter, joined to ch_item_doc.
create extension if not exists vector;
create table if not exists ch_chunk (
    item_id text not null, language text not null, chunk_no int not null, version int not null,
    text text not null, embedding vector(384), primary key (item_id, language, chunk_no));
