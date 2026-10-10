drop view if exists agent.guests;
create view agent.guests as select id, name, nationality from public.guest;
comment on view agent.guests is 'The hotel''s guests';
comment on column agent.guests.nationality is 'ISO-2';
