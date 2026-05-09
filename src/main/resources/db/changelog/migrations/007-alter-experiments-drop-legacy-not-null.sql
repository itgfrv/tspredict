--liquibase formatted sql

--changeset forecast:051-alter-experiments-drop-legacy-not-null
alter table experiments
    alter column model_id drop not null;

alter table experiments
    alter column parameters_json drop not null;

alter table experiments
    alter column result_json drop not null;