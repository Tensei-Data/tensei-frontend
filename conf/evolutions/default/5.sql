# --- !Ups

CREATE TABLE "dfasdlresources"
(
  "id" bigserial NOT NULL,
  "dfasdl" text,
  "public" character varying(32) NOT NULL,
  "privileges" text,
  CONSTRAINT "dfasdlresources_pk" PRIMARY KEY ("id")
);

# --- !Downs

DROP TABLE "dfasdlresources"