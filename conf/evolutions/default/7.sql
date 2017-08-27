# --- !Ups

CREATE TABLE "dfasdls"
(
  "id" character varying(200) NOT NULL,
  "content" text NOT NULL,
  "version" character varying(32) NOT NULL,
  CONSTRAINT "dfasdls_pk" PRIMARY KEY ("id", "version"),
  CONSTRAINT "dfasdls_unique" UNIQUE ("id", "version")
);

ALTER TABLE "dfasdlresources" DROP COLUMN "dfasdl";
ALTER TABLE "dfasdlresources" ADD COLUMN "dfasdl_id" character varying(200);
ALTER TABLE "dfasdlresources" ADD COLUMN "dfasdl_version" character varying(32);
ALTER TABLE "dfasdlresources" ADD CONSTRAINT "dfasdlresources_fk_dfasdls" FOREIGN KEY ("dfasdl_id", "dfasdl_version") REFERENCES "dfasdls" ("id", "version") ON UPDATE CASCADE ON DELETE CASCADE;

# --- !Downs

ALTER TABLE "dfasdlresources" DROP CONSTRAINT "dfasdlresources_fk_dfasdls";
ALTER TABLE "dfasdlresources" DROP COLUMN "dfasdl_version";
ALTER TABLE "dfasdlresources" DROP COLUMN "dfasdl_id";
ALTER TABLE "dfasdlresources" ADD COLUMN "dfasdl" TEXT;
DROP TABLE "dfasdls";
