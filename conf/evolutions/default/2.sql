# --- !Ups

CREATE TABLE "usergroups"
(
  "id" bigserial NOT NULL,
  "name" character varying(128) NOT NULL,
  CONSTRAINT "usergroups_pk" PRIMARY KEY ("id"),
  CONSTRAINT "usergroups_unique_name" UNIQUE ("name")
);

# --- !Downs

DROP TABLE "usergroups";