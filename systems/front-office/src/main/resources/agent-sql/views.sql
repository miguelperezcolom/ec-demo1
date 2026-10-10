-- The front office's data as the AI agents may read it (agent-sql, doc/src/content/docs/ia/datos-del-agente.md): views in the
-- `agent` schema, in the desk's language, run with this service's rights and read by a login that can
-- read nothing else. No documents, e-mails, phones or payment links: what a question about the hotel
-- needs, not what identifies or reaches a person. Recreated at every start (H2 and PostgreSQL alike),
-- so a view changed here is the new one; the comments are what describe tells the agent.

drop view if exists agent.stays;
create view agent.stays as
select s.id                                     as stay_id,
       w.locator                                as walk_in_locator,
       s.status,
       s.check_in,
       s.check_out,
       s.room_number,
       s.room_type,
       s.board,
       s.pax,
       s.agency,
       s.total,
       s.guest_id,
       g.name                                   as guest_name,
       g.tier                                   as guest_tier,
       coalesce(r.field_value, n.nationality)   as holder_nationality,
       s.vip_note
from stay s
join guest g on g.id = s.guest_id
left join walk_in w on w.stay_id = s.id
left join pax_registration_data r on r.stay_id = s.id and r.pax = 1 and r.field = 'NATIONALITY'
left join customer_nationality n on n.customer_id = s.guest_id;
comment on view agent.stays is 'One row per stay (a reservation at the hotel): arriving, in house, departed, cancelled or no-show. Its holder is the guest.';
comment on column agent.stays.stay_id is 'The stay: the CRS locator, or FO-... for a walk-in';
comment on column agent.stays.walk_in_locator is 'For a walk-in, its CRS locator once booked';
comment on column agent.stays.status is 'ARRIVING (still to check in), IN_HOUSE, DEPARTED, CANCELLED, NO_SHOW';
comment on column agent.stays.check_in is 'Arrival date';
comment on column agent.stays.check_out is 'Departure date; the stay occupies the nights from check_in to the night before check_out';
comment on column agent.stays.room_number is 'The room assigned; null until the desk assigns one';
comment on column agent.stays.room_type is 'Room type as the reservation says it, free text (Doble, Junior Suite...): filter with lower(...) like';
comment on column agent.stays.board is 'Board (meal plan), free text';
comment on column agent.stays.pax is 'How many people the stay hosts';
comment on column agent.stays.agency is 'Agency or channel it came from';
comment on column agent.stays.total is 'Price of the stay, EUR';
comment on column agent.stays.guest_tier is 'The holder''s loyalty tier';
comment on column agent.stays.holder_nationality is 'The holder''s nationality, ISO-2 (ES, DE, GB...): as scanned at the desk, else the chain''s; null if unknown';
comment on column agent.stays.vip_note is 'A VIP note for the desk, if any';

drop view if exists agent.stay_pax;
create view agent.stay_pax as
select s.id                                     as stay_id,
       1                                        as pax,
       true                                     as holder,
       s.guest_id                               as customer_id,
       g.name,
       coalesce(r.field_value, n.nationality)   as nationality,
       g.document_verified                      as identity_verified
from stay s
join guest g on g.id = s.guest_id
left join pax_registration_data r on r.stay_id = s.id and r.pax = 1 and r.field = 'NATIONALITY'
left join customer_nationality n on n.customer_id = s.guest_id
union all
select c.stay_id,
       c.idx + 2,
       false,
       c.companion_id,
       c.name,
       coalesce(r.field_value, n.nationality),
       c.document_verified
from stay_companion c
left join pax_registration_data r on r.stay_id = c.stay_id and r.pax = c.idx + 2 and r.field = 'NATIONALITY'
left join customer_nationality n on n.customer_id = c.companion_id;
comment on view agent.stay_pax is 'Every person of every stay: pax 1 is the holder, 2... the companions';
comment on column agent.stay_pax.customer_id is 'The chain''s customer code (C-...)';
comment on column agent.stay_pax.nationality is 'ISO-2 (ES, DE...): as scanned at the desk, else the chain''s; null if unknown';
comment on column agent.stay_pax.identity_verified is 'Whether the desk has verified their identity document';

drop view if exists agent.guests;
create view agent.guests as
select g.id                as guest_id,
       g.name,
       g.tier,
       g.loyalty_points,
       g.stays,
       g.nights,
       g.years_as_client,
       g.complaints,
       g.hotels,
       n.nationality
from guest g
left join customer_nationality n on n.customer_id = g.id;
comment on view agent.guests is 'The holders the front office knows, with their history in the chain (no contact or document data)';
comment on column agent.guests.guest_id is 'The chain''s customer code (C-...); agent.stays.guest_id';
comment on column agent.guests.tier is 'Loyalty tier';
comment on column agent.guests.stays is 'Stays in the chain';
comment on column agent.guests.nights is 'Nights in the chain';
comment on column agent.guests.complaints is 'Complaints they have made';
comment on column agent.guests.hotels is 'Hotels of the chain they have stayed at';
comment on column agent.guests.nationality is 'ISO-2, the chain''s; null if unknown';

drop view if exists agent.rooms;
create view agent.rooms as
select room_number, floor, type, occupancy, housekeeping, maintenance_note
from room;
comment on view agent.rooms is 'The hotel''s rooms and their state now';
comment on column agent.rooms.type is 'Room type, free text';
comment on column agent.rooms.occupancy is 'FREE or OCCUPIED';
comment on column agent.rooms.housekeeping is 'DIRTY, CLEAN or INSPECTED';
comment on column agent.rooms.maintenance_note is 'An open maintenance issue, if any';

drop view if exists agent.incidents;
create view agent.incidents as
select stay_id, code, type, title, description, status, complaint, opened_at, resolved_at
from stay_incident;
comment on view agent.incidents is 'Incidents reported during stays';
comment on column agent.incidents.type is 'TV, CLIMA, SERVICIO, RESTAURANTE, LIMPIEZA, GENERAL';
comment on column agent.incidents.status is 'OPEN, IN_PROGRESS, RESOLVED';
comment on column agent.incidents.complaint is 'Whether the guest complained';

drop view if exists agent.folio_lines;
create view agent.folio_lines as
select f.stay_id, l.idx as line, l.concept, l.amount, l.included, l.included_label
from folio f
join folio_line l on l.folio_id = f.id;
comment on view agent.folio_lines is 'The charges on each stay''s folio (only stays that checked in have one)';
comment on column agent.folio_lines.amount is 'EUR';
comment on column agent.folio_lines.included is 'Included in the price: does not add to the balance';
comment on column agent.folio_lines.included_label is 'Why it is included or that it was voided';

drop view if exists agent.payments;
create view agent.payments as
select stay_id, kind, method, amount, currency, status, created_at, captured_at
from folio_payment;
comment on view agent.payments is 'Payments taken at the desk''s till for stays';
comment on column agent.payments.kind is 'PAYMENT or DEPOSIT';
comment on column agent.payments.method is 'CASH, CARD_PINPAD, PAY_LINK, TRANSFER, MANUAL';
comment on column agent.payments.status is 'PENDING, CAPTURED (money in), DECLINED, CANCELLED';
