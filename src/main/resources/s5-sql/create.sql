create table job (
    id varchar(36),
    directory varchar(4096),
    retention_policy varchar(16),
    last_started timestamp,
    last_finished timestamp,

    primary key (id)
);
