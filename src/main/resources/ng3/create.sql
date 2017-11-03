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