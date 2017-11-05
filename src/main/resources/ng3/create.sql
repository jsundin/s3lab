create table plan (
    plan_id varchar(36),
    last_started timestamp,

    primary key (plan_id)
);

create table directory (
    directory_id varchar(36),
    plan_id varchar(36),
    directory varchar(4096),

    primary key (directory_id),
    foreign key (plan_id) references plan (plan_id)
);

create table file (
    file_id varchar(36),
    directory_id varchar(36),
    filename varchar(4096),
    last_modified timestamp,
    upload_started timestamp,
    upload_finished timestamp,

    primary key (file_id),
    foreign key (directory_id) references directory (directory_id)
);