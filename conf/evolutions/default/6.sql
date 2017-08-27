# --- !Ups

CREATE TABLE "cookbookresources"
(
  "id" bigserial NOT NULL,
  "cookbook" text,
  "public" character varying(32) NOT NULL,
  "privileges" text,
  CONSTRAINT "cookbookresources_pk" PRIMARY KEY ("id")
);

# --- !Downs

DROP TABLE "cookbookresources"