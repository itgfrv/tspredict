--liquibase formatted sql

--changeset forecast:060-alter-experiments-add-ensemble-config
alter table experiments
    add column ensemble_mode varchar(50) not null default 'NONE';

alter table experiments
    add column ensemble_config_json jsonb;