--liquibase formatted sql

--changeset forecast:061-alter-experiment-runs-add-external-job-id
alter table experiment_runs
    add column external_job_id text;
