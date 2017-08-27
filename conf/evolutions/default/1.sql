# --- !Ups

CREATE TABLE "accounts"
(
  "id" bigserial NOT NULL,
  "email" character varying(128) NOT NULL,
  "name" character varying(128) NOT NULL,
  "password" character varying(256) NOT NULL,
  CONSTRAINT "accounts_pk" PRIMARY KEY ("id"),
  CONSTRAINT "accounts_unique_email" UNIQUE ("email")
);

# --- !Downs

DROP TABLE "accounts";