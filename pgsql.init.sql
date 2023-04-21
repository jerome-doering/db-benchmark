create table public.lookup
(
    id          varchar(512)       not null
        constraint lookup_pk
            primary key,
    archival_id serial             not null
        constraint lookup_archival_id
            unique,
    timestamp   timestamp          not null,
    created_at  timestamp          not null,
    archived_at timestamp,
    identifiers jsonb default '{}' not null
);

create index identifier_lookup_values
    on public.lookup USING GIN (identifiers);

