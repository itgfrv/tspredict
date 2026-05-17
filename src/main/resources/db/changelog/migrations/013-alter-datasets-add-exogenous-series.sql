--liquibase formatted sql

--changeset forecast:063-alter-datasets-add-exogenous-series
alter table datasets
    add column exogenous_column_names_json jsonb,
    add column exogenous_row_indexes_json jsonb,
    add column target_series_name varchar(255),
    add column exogenous_series_names_json jsonb;
