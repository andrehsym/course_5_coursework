-- liquibase formatted sql

-- changeSet andrehsym:1

create table notification_task (
 id SERIAL,
 chatId BIGSERIAL,
 notification TEXT,
 dateTime TIMESTAMP
)

-- changeSet andrehsym:2

ALTER TABLE notification_task
ADD PRIMARY KEY (id);