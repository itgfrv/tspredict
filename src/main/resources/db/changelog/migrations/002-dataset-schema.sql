--liquibase formatted sql

--changeset forecast:010-create-datasets
create table datasets (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,

    name varchar(255) not null,
    source_file_name varchar(255) not null,
    source_file_path text not null,
    normalized_csv_path text,

    sheet_name varchar(255) not null,
    orientation varchar(50) not null,
    date_column_name varchar(255),
    value_column_name varchar(255),
    date_row_index integer,
    value_row_index integer,

    frequency varchar(50),
    points_count integer not null default 0,

    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);