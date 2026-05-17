--liquibase formatted sql

--changeset forecast:063-alter-model-registry-add-request-routing-key
alter table model_registry
    add column request_routing_key text;
