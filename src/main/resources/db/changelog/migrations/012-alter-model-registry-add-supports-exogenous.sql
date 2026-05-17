--liquibase formatted sql

--changeset forecast:062-alter-model-registry-add-supports-exogenous
alter table model_registry
    add column supports_exogenous boolean not null default false;
