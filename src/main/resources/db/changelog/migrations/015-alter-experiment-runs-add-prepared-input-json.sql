--liquibase formatted sql

--changeset forecast:064-alter-experiment-runs-add-prepared-input-json
alter table experiment_runs
    add column prepared_input_json jsonb;
