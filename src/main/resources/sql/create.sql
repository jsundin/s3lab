create table file (
    id varchar(40),
    filename varchar(4096),

    primary key (id)
);

create table file_version (
    id varchar(40),
    file_id varchar(40),
    version integer,
    modified timestamp,
    deleted boolean,

    primary key (id),
    foreign key (file_id) references file (id),
    unique (file_id, version)
);

create index file_version_version on file_version (version);
