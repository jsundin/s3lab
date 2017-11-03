create table state (
    last_started timestamp
);

create table directory (
    directory_id varchar(36),
    directory varchar(4096),

    primary key (directory_id)
);