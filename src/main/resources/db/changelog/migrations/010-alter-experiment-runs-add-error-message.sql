--liquibase formatted sql

--changeset forecast:060-alter-experiment-runs-add-error-message
alter table experiment_runs
    add column error_message text;
