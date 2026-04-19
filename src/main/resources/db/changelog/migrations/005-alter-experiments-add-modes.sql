--liquibase formatted sql

--changeset forecast:040-alter-experiments-add-modes
alter table experiments
    add column forecast_mode varchar(50) not null default 'OUT_OF_SAMPLE';

alter table experiments
    add column decomposition_enabled boolean not null default false;

alter table experiments
    add column ensemble_enabled boolean not null default false;