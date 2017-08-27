# --- !Ups

CREATE TABLE "triggers"
(
  "id" bigserial NOT NULL,
  "tkid" bigint,
  "description" character varying(254),
  "endpoint_uri" text NOT NULL,
  "active" boolean DEFAULT true,
  "public" character varying(32) NOT NULL,
  "privileges" text,
  CONSTRAINT "triggers_pk" PRIMARY KEY ("id")
);

# --- !Downs

DROP TABLE "triggers"