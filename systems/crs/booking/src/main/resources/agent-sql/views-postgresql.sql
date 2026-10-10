-- The CRS's bookings as the AI agents may read them (agent-sql, doc/src/content/docs/ia/datos-del-agente.md): views in the
-- `agent` schema over crs_booking, its JSON (holder, rooms, guests, payments) laid out in rows, run
-- with this service's rights and read by a login that can read nothing else. No e-mails, phones,
-- documents or birth dates. PostgreSQL only (jsonb): on H2 the CRS has no agent views. Recreated at
-- every start, after Hibernate's ddl-auto; the comments are what describe tells the agent.
--
-- A date inside the JSON may be ISO text or [y, m, d], as the JSON mapper wrote it: the views read both.

drop view if exists agent.bookings;
create view agent.bookings as
select b.id                                        as booking_id,
       b.hotel_code,
       b.status,
       b.channel_code,
       b.partner_code,
       b.external_reference,
       b.arrival,
       b.departure,
       b.departure - b.arrival                     as nights,
       b.holder_name,
       upper(b.holder ->> 'nationality')           as holder_nationality,
       jsonb_array_length(b.rooms::jsonb)          as rooms,
       coalesce(b.cancellation_fee, (select sum((n ->> 'amount')::numeric)
                                     from jsonb_array_elements(b.rooms::jsonb) r,
                                          jsonb_array_elements(r -> 'nightlyRates') n)) as total,
       b.currency,
       b.cancellation_reason,
       b.cancelled_at,
       b.cancellation_fee,
       b.pms_reservation_id,
       b.created,
       b.updated
from crs_booking b;
comment on view agent.bookings is 'One row per CRS booking, every hotel';
comment on column agent.bookings.booking_id is 'The CRS locator';
comment on column agent.bookings.hotel_code is 'CRS hotel code (MRU01...)';
comment on column agent.bookings.status is 'Pending, Confirmed or Cancelled (case as written)';
comment on column agent.bookings.channel_code is 'Sales channel code (WEB, TTOO, OTA...); names in getCatalog';
comment on column agent.bookings.partner_code is 'Tour operator or OTA, for those channels';
comment on column agent.bookings.external_reference is 'The partner''s reference (voucher)';
comment on column agent.bookings.nights is 'Nights of the stay';
comment on column agent.bookings.holder_nationality is 'The holder''s nationality, ISO-2; null if unknown';
comment on column agent.bookings.rooms is 'How many rooms';
comment on column agent.bookings.total is 'What the booking costs now: the sum of its nightly rates, or the cancellation fee if one applies';
comment on column agent.bookings.cancellation_reason is 'Cancellation reason code (NOS = no-show)';
comment on column agent.bookings.pms_reservation_id is 'Its reservation in the PMS (Opera), once there';
comment on column agent.bookings.created is 'When it was booked';

drop view if exists agent.booking_rooms;
create view agent.booking_rooms as
select b.id                                        as booking_id,
       b.hotel_code,
       (r ->> 'line')::int                         as line,
       r ->> 'roomTypeCode'                        as room_type_code,
       r ->> 'ratePlanCode'                        as rate_plan_code,
       r ->> 'boardCode'                           as board_code,
       (r ->> 'adults')::int                       as adults,
       coalesce(jsonb_array_length(r -> 'childrenAges'), 0) as children,
       (select sum((n ->> 'amount')::numeric) from jsonb_array_elements(r -> 'nightlyRates') n) as amount
from crs_booking b, jsonb_array_elements(b.rooms::jsonb) r;
comment on view agent.booking_rooms is 'Every room of every booking: its codes, occupancy and price';
comment on column agent.booking_rooms.room_type_code is 'CRS room type code (DBL-GARDEN, JS-STD...); names in getCatalog: doubles are DBL..., junior suites JS...';
comment on column agent.booking_rooms.rate_plan_code is 'Rate plan code';
comment on column agent.booking_rooms.board_code is 'Board code (AD, MP, TI...)';
comment on column agent.booking_rooms.amount is 'The room''s price: the sum of its nightly rates';

drop view if exists agent.booking_guests;
create view agent.booking_guests as
select b.id                                        as booking_id,
       (r ->> 'line')::int                         as line,
       g ->> 'firstName'                           as first_name,
       g ->> 'lastName'                            as last_name,
       g ->> 'type'                                as type,
       (g ->> 'age')::int                          as age,
       upper(g ->> 'nationality')                  as nationality
from crs_booking b, jsonb_array_elements(b.rooms::jsonb) r, jsonb_array_elements(r -> 'guests') g;
comment on view agent.booking_guests is 'The guests named in each room of each booking (often only the holder until check-in)';
comment on column agent.booking_guests.line is 'The room: agent.booking_rooms.line';
comment on column agent.booking_guests.type is 'Adult or Child';
comment on column agent.booking_guests.age is 'A child''s age';
comment on column agent.booking_guests.nationality is 'ISO-2; null if unknown';

drop view if exists agent.booking_payments;
create view agent.booking_payments as
select b.id                                        as booking_id,
       p ->> 'type'                                as type,
       p ->> 'methodCode'                          as method_code,
       (p ->> 'amount')::numeric                   as amount,
       case jsonb_typeof(p -> 'date')
           when 'array' then make_date((p -> 'date' ->> 0)::int, (p -> 'date' ->> 1)::int, (p -> 'date' ->> 2)::int)
           else (p ->> 'date')::date end           as date
from crs_booking b, jsonb_array_elements(coalesce(b.payments::jsonb, '[]'::jsonb)) p;
comment on view agent.booking_payments is 'Money the central office has collected for bookings';
comment on column agent.booking_payments.type is 'Deposit or Prepayment';
comment on column agent.booking_payments.method_code is 'Payment method code; names in getCatalog';
