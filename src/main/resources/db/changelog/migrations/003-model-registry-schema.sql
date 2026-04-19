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