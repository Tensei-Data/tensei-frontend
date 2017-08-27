# --- !Ups

ALTER TABLE "transformationconfigurations" ADD COLUMN "dirty" boolean DEFAULT FALSE;
ALTER TABLE "transformationconfigurationconnectionsources" DROP CONSTRAINT "transformationconfigurationconnectionsources_fk_transformationconfiguration_id";
ALTER TABLE "transformationconfigurationconnectionsources" DROP CONSTRAINT "transformationconfigurationconnectionsources_fk_connectioninformations_id";
ALTER TABLE "transformationconfigurationconnectionsources" DROP CONSTRAINT "transformationconfigurationconnectionsources_fk_dfasdlresource_id";
ALTER TABLE "transformationconfigurationconnectionsources" DROP CONSTRAINT "transformationconfigurationconnectionsources_pk";
ALTER TABLE "transformationconfigurationconnectionsources" ADD CONSTRAINT "transformationconfigurationconnectionsources_fk_transformationconfiguration_id" FOREIGN KEY ("transformationconfiguration_id") REFERENCES "transformationconfigurations" ("id") ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE "transformationconfigurationconnectionsources" ADD CONSTRAINT "transformationconfigurationconnectionsources_fk_connectioninformations_id" FOREIGN KEY ("connectioninformationresource_id") REFERENCES "connectioninformations" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "transformationconfigurationconnectionsources" ADD CONSTRAINT "transformationconfigurationconnectionsources_fk_dfasdlresource_id" FOREIGN KEY ("dfasdlresource_id") REFERENCES "dfasdlresources" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "transformationconfigurationconnectionsources" ADD CONSTRAINT "transformationconfigurationconnectionsources_pk" PRIMARY KEY ("transformationconfiguration_id", "dfasdlresource_id");

# --- !Downs

ALTER TABLE "transformationconfigurations" DROP COLUMN "dirty";
ALTER TABLE "transformationconfigurationconnectionsources" DROP CONSTRAINT "transformationconfigurationconnectionsources_fk_transformationconfiguration_id";
ALTER TABLE "transformationconfigurationconnectionsources" DROP CONSTRAINT "transformationconfigurationconnectionsources_fk_connectioninformations_id";
ALTER TABLE "transformationconfigurationconnectionsources" DROP CONSTRAINT "transformationconfigurationconnectionsources_fk_dfasdlresource_id";
ALTER TABLE "transformationconfigurationconnectionsources" DROP CONSTRAINT "transformationconfigurationconnectionsources_pk";
ALTER TABLE "transformationconfigurationconnectionsources" ADD CONSTRAINT "transformationconfigurationconnectionsources_fk_transformationconfiguration_id" FOREIGN KEY ("transformationconfiguration_id") REFERENCES "transformationconfigurations" ("id") ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE "transformationconfigurationconnectionsources" ADD CONSTRAINT "transformationconfigurationconnectionsources_fk_connectioninformations_id" FOREIGN KEY ("connectioninformationresource_id") REFERENCES "connectioninformations" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "transformationconfigurationconnectionsources" ADD CONSTRAINT "transformationconfigurationconnectionsources_fk_dfasdlresource_id" FOREIGN KEY ("dfasdlresource_id") REFERENCES "dfasdlresources" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "transformationconfigurationconnectionsources" ADD CONSTRAINT "transformationconfigurationconnectionsources_pk" PRIMARY KEY ("transformationconfiguration_id", "connectioninformationresource_id");
