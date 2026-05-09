--liquibase formatted sql

--changeset forecast:001-create-users
create table users (
    id uuid primary key,
    email varchar(255) not null unique,
    name varchar(255) not null,
    password_hash varchar(255) not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

--changeset forecast:002-create-projects
create table projects (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    name varchar(255) not null,
    description text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

--changeset forecast:003-create-indexes
create index idx_users_email on users(email);
create index idx_projects_user_id on projects(user_id);
create index idx_projects_created_at on projects(created_at);
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
--liquibase formatted sql

--changeset forecast:020-create-model-registry
create table model_registry (
    id uuid primary key,
    model_key varchar(100) not null unique,
    display_name varchar(255) not null,
    kind varchar(50) not null,
    service_url text not null,
    enabled boolean not null default true,
    supports_async boolean not null default false,
    description text,
    metadata_json jsonb,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);
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
--liquibase formatted sql

--changeset forecast:040-alter-experiments-add-modes
alter table experiments
    add column forecast_mode varchar(50) not null default 'OUT_OF_SAMPLE';

alter table experiments
    add column decomposition_enabled boolean not null default false;

alter table experiments
    add column ensemble_enabled boolean not null default false;
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
--liquibase formatted sql

--changeset forecast:051-alter-experiments-drop-legacy-not-null
alter table experiments
    alter column model_id drop not null;

alter table experiments
    alter column parameters_json drop not null;

alter table experiments
    alter column result_json drop not null;
    --liquibase formatted sql

    --changeset forecast:060-alter-experiments-add-ensemble-config
    alter table experiments
        add column ensemble_mode varchar(50) not null default 'NONE';

    alter table experiments
        add column ensemble_config_json jsonb;
        --liquibase formatted sql

        --changeset forecast:061-alter-experiments-add-ensemble-result
        alter table experiments
            add column ensemble_result_json jsonb;