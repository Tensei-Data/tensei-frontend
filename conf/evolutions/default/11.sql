# --- !Ups

CREATE TABLE "crontab"
(
  "id" bigserial NOT NULL,
  "tkid" bigint,
  "description" character varying(254),
  "format" text NOT NULL,
  "active" boolean DEFAULT true,
  "public" character varying(32) NOT NULL,
  "privileges" text,
  CONSTRAINT "crontab_pk" PRIMARY KEY ("id")
);

# --- !Downs

DROP TABLE "crontab"