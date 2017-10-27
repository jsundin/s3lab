create table job (
    id varchar(36),
    directory varchar(4096),
    retention_policy varchar(16),
    last_started timestamp,
    last_finished timestamp,

    primary key (id)
);

create table file (
    id varchar(36),
    job_id varchar(36),
    filename varchar(4096),
    last_modified timestamp,
    last_upload_start timestamp,
    last_upload_finished timestamp,

    primary key (id),
    foreign key (job_id) references job (id)
);