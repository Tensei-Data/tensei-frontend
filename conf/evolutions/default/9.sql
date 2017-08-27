# --- !Ups

CREATE TABLE "connectioninformations"
(
  "id" bigserial NOT NULL,
  "uri" text NOT NULL,
  "cookbook_id" character varying(200),
  "dfasdl_id" character varying(200),
  "username" character varying(254),
  "password" text,
  "checksum" text,
  "public" character varying(32) NOT NULL,
  "privileges" text,
  CONSTRAINT "connectioninformations_pk" PRIMARY KEY ("id"),
  CONSTRAINT "connectioninformations_fk_cookbook_id" FOREIGN KEY ("cookbook_id")
      REFERENCES "cookbooks" ("id")
      ON UPDATE CASCADE ON DELETE CASCADE
);

# --- !Downs

DROP TABLE "connectioninformations"