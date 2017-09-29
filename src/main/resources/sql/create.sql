create table directory_config (
  id varchar(40),
  path varchar(4096),
  retention_policy varchar(16),
  last_scan timestamp,

  primary key (id)
);

create table file (
    id varchar(40),
    filename varchar(4096),
    directory_id varchar(40) not null,

    primary key (id),
    foreign key (directory_id) references directory_config (id)
);

create table file_version (
    file_id varchar(40) not null,
    version integer,
    modified timestamp,
    deleted boolean,

    primary key (file_id, version),
    foreign key (file_id) references file (id)
);

create index file_version_version on file_version (version);
create index file_version_modified on file_version (modified);
