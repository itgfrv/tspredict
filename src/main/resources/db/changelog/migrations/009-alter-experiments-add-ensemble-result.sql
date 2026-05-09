--liquibase formatted sql

--changeset forecast:061-alter-experiments-add-ensemble-result
alter table experiments
    add column ensemble_result_json jsonb;