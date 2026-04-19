--liquibase formatted sql

--changeset forecast:030-create-experiments
create table experiments (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    dataset_id uuid not null references datasets(id) on delete cascade,
    model_id uuid not null references model_registry(id),

    name varchar(255) not null,
    status varchar(50) not null,
    horizon integer not null,

    parameters_json jsonb,
    result_json jsonb,

    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);