--liquibase formatted sql

--changeset forecast:050-create-experiment-runs
create table experiment_runs (
    id uuid primary key,
    experiment_id uuid not null references experiments(id) on delete cascade,
    model_id uuid not null references model_registry(id),

    status varchar(50) not null,
    parameters_json jsonb,
    result_json jsonb,

    mae double precision,
    rmse double precision,

    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);