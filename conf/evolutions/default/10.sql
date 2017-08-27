# --- !Ups

CREATE TABLE "transformationconfigurations"
(
  "id" bigserial NOT NULL,
  "name" character varying(254),
  "targetResourceId" bigint,
  "cookbookResourceId" bigint,
  "public" character varying(32),
  "privileges" text,
  CONSTRAINT "transformationconfigurations_pk" PRIMARY KEY ("id"),
  CONSTRAINT "transformationconfigurations_fk_connectioninformations_id" FOREIGN KEY ("targetResourceId") REFERENCES "connectioninformations" ("id")  ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT "transformationconfigurations_fk_cookbookresources_id" FOREIGN KEY ("cookbookResourceId") REFERENCES "cookbookresources" ("id")  ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE "transformationconfigurationconnectionsources"
(
  "transformationconfiguration_id" BIGINT,
  "connectionresource_id"          BIGINT,
  CONSTRAINT "transformationconfigurationconnectionsources_pk" PRIMARY KEY ("transformationconfiguration_id", "connectionresource_id"),
  CONSTRAINT "transformationconfigurationconnectionsources_fk_transformationconfiguration_id" FOREIGN KEY ("transformationconfiguration_id") REFERENCES "transformationconfigurations" ("id") ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT "transformationconfigurationconnectionsources_fk_connectioninformations_id" FOREIGN KEY ("connectionresource_id") REFERENCES "connectioninformations" ("id") ON UPDATE CASCADE ON DELETE RESTRICT
);

# --- !Downs

DROP TABLE "transformationconfigurationconnectionsources";
DROP TABLE "transformationconfigurations";
