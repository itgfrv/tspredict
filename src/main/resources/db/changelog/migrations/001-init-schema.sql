--liquibase formatted sql

--changeset forecast:001-create-users
create table users (
    id uuid primary key,
    email varchar(255) not null unique,
    name varchar(255) not null,
    password_hash varchar(255) not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

--changeset forecast:002-create-projects
create table projects (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    name varchar(255) not null,
    description text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

--changeset forecast:003-create-indexes
create index idx_users_email on users(email);
create index idx_projects_user_id on projects(user_id);
create index idx_projects_created_at on projects(created_at);