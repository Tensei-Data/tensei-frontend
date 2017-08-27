# --- !Ups

DROP TABLE IF EXISTS "accountgroupmappings";
DROP TABLE IF EXISTS "usergroups";
DROP TABLE IF EXISTS "accounts";

CREATE TABLE "accounts"
(
  "id" serial,
  "email" character varying(128) NOT NULL,
  "password" character varying(60),
  "is_admin" boolean DEFAULT false,
  "failed_login_attempts" integer DEFAULT 0,
  "locked_at" timestamp DEFAULT NULL,
  "unlock_token" character varying(128) DEFAULT NULL,
  CONSTRAINT "accounts_pk" PRIMARY KEY ("id"),
  CONSTRAINT "accounts_unique_email" UNIQUE ("email"),
  CONSTRAINT "accounts_unique_unlock_token" UNIQUE ("unlock_token")
);
COMMENT ON TABLE "accounts" IS 'This table holds the user accounts for the system.';
COMMENT ON COLUMN "accounts"."id" IS 'The ID of the account.';
COMMENT ON COLUMN "accounts"."email" IS 'The email is the primary identification for a user account and must never be NULL or longer than 128 characters.';
COMMENT ON COLUMN "accounts"."password" IS 'The hashed user password.';
COMMENT ON COLUMN "accounts"."is_admin" IS 'This flag indicates if the account is a system administrator.';
COMMENT ON COLUMN "accounts"."failed_login_attempts" IS 'The number of failed login attempts since the last successful login.';
COMMENT ON COLUMN "accounts"."locked_at" IS 'A timestamp holding the information when the account was locked.';
COMMENT ON COLUMN "accounts"."unlock_token" IS 'The token that will allow the account to be unlocked again.';

CREATE TABLE "groups"
(
  "id" serial,
  "name" character varying(128) NOT NULL,
  CONSTRAINT "groups_pk" PRIMARY KEY ("id"),
  CONSTRAINT "groups_unique_name" UNIQUE ("name")
);
COMMENT ON TABLE "groups" IS 'This table holds the user groups.';
COMMENT ON COLUMN "groups"."id" IS 'The id of the group.';
COMMENT ON COLUMN "groups"."name" IS 'The name of the group which must be unique and not longer than 128 characters.';

CREATE TABLE "accounts_groups"
(
  "account_id" integer NOT NULL,
  "group_id" integer NOT NULL,
  CONSTRAINT "accounts_groups_pk" PRIMARY KEY ("account_id", "group_id"),
  CONSTRAINT "accounts_groups_fk_account_id" FOREIGN KEY ("account_id") REFERENCES "accounts" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "accounts_groups_fk_group_id" FOREIGN KEY ("group_id") REFERENCES "groups" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
COMMENT ON TABLE "accounts_groups" IS 'This table holds the mappings of users to groups.';
COMMENT ON COLUMN "accounts_groups"."account_id" IS 'The ID of the user account. Deleting or updating the account will cascade through here.';
COMMENT ON COLUMN "accounts_groups"."group_id" IS 'The ID of the group. Deleting or updating the group will cascade through here.';

ALTER TABLE "connectioninformations" DROP COLUMN "public";
ALTER TABLE "connectioninformations" DROP COLUMN "privileges";
ALTER TABLE "connectioninformations" ADD COLUMN "owner_id" integer NOT NULL;
ALTER TABLE "connectioninformations" ADD COLUMN "group_id" integer;
ALTER TABLE "connectioninformations" ADD COLUMN "group_permissions" integer NOT NULL DEFAULT 6;
ALTER TABLE "connectioninformations" ADD COLUMN "world_permissions" integer NOT NULL DEFAULT 4;
ALTER TABLE "connectioninformations" ADD CONSTRAINT "connectioninformations_fk_owner_id" FOREIGN KEY ("owner_id") REFERENCES "accounts" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "connectioninformations" ADD CONSTRAINT "connectioninformations_fk_group_id" FOREIGN KEY ("group_id") REFERENCES "groups" ("id") ON UPDATE CASCADE ON DELETE SET NULL;
COMMENT ON COLUMN "connectioninformations"."owner_id" IS 'The id of the user account that owns the resource.';
COMMENT ON COLUMN "connectioninformations"."group_id" IS 'The id of the group that owns the resource which may be NULL.';
COMMENT ON COLUMN "connectioninformations"."group_permissions" IS 'The encoded permissions for the group which defaults to read and write access.';
COMMENT ON COLUMN "connectioninformations"."world_permissions" IS 'The encoded permissions for the world which defaults to read access.';

ALTER TABLE "cookbookresources" DROP COLUMN "public";
ALTER TABLE "cookbookresources" DROP COLUMN "privileges";
ALTER TABLE "cookbookresources" ADD COLUMN "owner_id" integer NOT NULL;
ALTER TABLE "cookbookresources" ADD COLUMN "group_id" integer;
ALTER TABLE "cookbookresources" ADD COLUMN "group_permissions" integer NOT NULL DEFAULT 6;
ALTER TABLE "cookbookresources" ADD COLUMN "world_permissions" integer NOT NULL DEFAULT 4;
ALTER TABLE "cookbookresources" ADD CONSTRAINT "cookbookresources_fk_owner_id" FOREIGN KEY ("owner_id") REFERENCES "accounts" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "cookbookresources" ADD CONSTRAINT "cookbookresources_fk_group_id" FOREIGN KEY ("group_id") REFERENCES "groups" ("id") ON UPDATE CASCADE ON DELETE SET NULL;
COMMENT ON COLUMN "cookbookresources"."owner_id" IS 'The id of the user account that owns the resource.';
COMMENT ON COLUMN "cookbookresources"."group_id" IS 'The id of the group that owns the resource which may be NULL.';
COMMENT ON COLUMN "cookbookresources"."group_permissions" IS 'The encoded permissions for the group which defaults to read and write access.';
COMMENT ON COLUMN "cookbookresources"."world_permissions" IS 'The encoded permissions for the world which defaults to read access.';

ALTER TABLE "crontab" DROP COLUMN "public";
ALTER TABLE "crontab" DROP COLUMN "privileges";
ALTER TABLE "crontab" ADD COLUMN "owner_id" integer NOT NULL;
ALTER TABLE "crontab" ADD COLUMN "group_id" integer;
ALTER TABLE "crontab" ADD COLUMN "group_permissions" integer NOT NULL DEFAULT 6;
ALTER TABLE "crontab" ADD COLUMN "world_permissions" integer NOT NULL DEFAULT 4;
ALTER TABLE "crontab" ADD CONSTRAINT "crontab_fk_tkid" FOREIGN KEY ("tkid") REFERENCES "transformationconfigurations" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "crontab" ADD CONSTRAINT "crontab_fk_owner_id" FOREIGN KEY ("owner_id") REFERENCES "accounts" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "crontab" ADD CONSTRAINT "crontab_fk_group_id" FOREIGN KEY ("group_id") REFERENCES "groups" ("id") ON UPDATE CASCADE ON DELETE SET NULL;
COMMENT ON COLUMN "crontab"."owner_id" IS 'The id of the user account that owns the resource.';
COMMENT ON COLUMN "crontab"."group_id" IS 'The id of the group that owns the resource which may be NULL.';
COMMENT ON COLUMN "crontab"."group_permissions" IS 'The encoded permissions for the group which defaults to read and write access.';
COMMENT ON COLUMN "crontab"."world_permissions" IS 'The encoded permissions for the world which defaults to read access.';
ALTER TABLE "crontab" ALTER COLUMN "tkid" SET NOT NULL;

ALTER TABLE "dfasdlresources" DROP COLUMN "public";
ALTER TABLE "dfasdlresources" DROP COLUMN "privileges";
ALTER TABLE "dfasdlresources" ADD COLUMN "owner_id" integer NOT NULL;
ALTER TABLE "dfasdlresources" ADD COLUMN "group_id" integer;
ALTER TABLE "dfasdlresources" ADD COLUMN "group_permissions" integer NOT NULL DEFAULT 6;
ALTER TABLE "dfasdlresources" ADD COLUMN "world_permissions" integer NOT NULL DEFAULT 4;
ALTER TABLE "dfasdlresources" ADD CONSTRAINT "dfasdlresources_fk_owner_id" FOREIGN KEY ("owner_id") REFERENCES "accounts" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "dfasdlresources" ADD CONSTRAINT "dfasdlresources_fk_group_id" FOREIGN KEY ("group_id") REFERENCES "groups" ("id") ON UPDATE CASCADE ON DELETE SET NULL;
COMMENT ON COLUMN "dfasdlresources"."owner_id" IS 'The id of the user account that owns the resource.';
COMMENT ON COLUMN "dfasdlresources"."group_id" IS 'The id of the group that owns the resource which may be NULL.';
COMMENT ON COLUMN "dfasdlresources"."group_permissions" IS 'The encoded permissions for the group which defaults to read and write access.';
COMMENT ON COLUMN "dfasdlresources"."world_permissions" IS 'The encoded permissions for the world which defaults to read access.';

ALTER TABLE "transformationconfigurations" DROP COLUMN "public";
ALTER TABLE "transformationconfigurations" DROP COLUMN "privileges";
ALTER TABLE "transformationconfigurations" ADD COLUMN "owner_id" integer NOT NULL;
ALTER TABLE "transformationconfigurations" ADD COLUMN "group_id" integer;
ALTER TABLE "transformationconfigurations" ADD COLUMN "group_permissions" integer NOT NULL DEFAULT 6;
ALTER TABLE "transformationconfigurations" ADD COLUMN "world_permissions" integer NOT NULL DEFAULT 4;
ALTER TABLE "transformationconfigurations" ADD CONSTRAINT "transformationconfigurations_fk_owner_id" FOREIGN KEY ("owner_id") REFERENCES "accounts" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "transformationconfigurations" ADD CONSTRAINT "transformationconfigurations_fk_group_id" FOREIGN KEY ("group_id") REFERENCES "groups" ("id") ON UPDATE CASCADE ON DELETE SET NULL;
COMMENT ON COLUMN "transformationconfigurations"."owner_id" IS 'The id of the user account that owns the resource.';
COMMENT ON COLUMN "transformationconfigurations"."group_id" IS 'The id of the group that owns the resource which may be NULL.';
COMMENT ON COLUMN "transformationconfigurations"."group_permissions" IS 'The encoded permissions for the group which defaults to read and write access.';
COMMENT ON COLUMN "transformationconfigurations"."world_permissions" IS 'The encoded permissions for the world which defaults to read access.';

ALTER TABLE "transformationconfigurations" RENAME COLUMN "cookbookResourceId" TO "cookbookresource_id";
ALTER TABLE "transformationconfigurations" RENAME COLUMN "targetResourceId" TO "target_connectioninformationresource_id";
ALTER TABLE "transformationconfigurations" ADD COLUMN "target_dfasdlresource_id" bigint;
ALTER TABLE "transformationconfigurations" ADD CONSTRAINT "transformationconfigurations_fk_dfasdlresources_id" FOREIGN KEY ("target_dfasdlresource_id") REFERENCES "dfasdlresources" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE "transformationconfigurationconnectionsources" RENAME COLUMN "connectionresource_id" TO "connectioninformationresource_id";
ALTER TABLE "transformationconfigurationconnectionsources" ADD COLUMN "dfasdlresource_id" bigint NOT NULL;
ALTER TABLE "transformationconfigurationconnectionsources" ADD CONSTRAINT "transformationconfigurationconnectionsources_fk_dfasdlresource_id" FOREIGN KEY ("dfasdlresource_id") REFERENCES "dfasdlresources" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE "triggers" DROP COLUMN "public";
ALTER TABLE "triggers" DROP COLUMN "privileges";
ALTER TABLE "triggers" ADD COLUMN "owner_id" integer NOT NULL;
ALTER TABLE "triggers" ADD COLUMN "group_id" integer;
ALTER TABLE "triggers" ADD COLUMN "group_permissions" integer NOT NULL DEFAULT 6;
ALTER TABLE "triggers" ADD COLUMN "world_permissions" integer NOT NULL DEFAULT 4;
ALTER TABLE "triggers" ADD CONSTRAINT "triggers_fk_owner_id" FOREIGN KEY ("owner_id") REFERENCES "accounts" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE "triggers" ADD CONSTRAINT "triggers_fk_group_id" FOREIGN KEY ("group_id") REFERENCES "groups" ("id") ON UPDATE CASCADE ON DELETE SET NULL;
COMMENT ON COLUMN "triggers"."owner_id" IS 'The id of the user account that owns the resource.';
COMMENT ON COLUMN "triggers"."group_id" IS 'The id of the group that owns the resource which may be NULL.';
COMMENT ON COLUMN "triggers"."group_permissions" IS 'The encoded permissions for the group which defaults to read and write access.';
COMMENT ON COLUMN "triggers"."world_permissions" IS 'The encoded permissions for the world which defaults to read access.';
ALTER TABLE "triggers" ALTER COLUMN "tkid" SET NOT NULL;

UPDATE "tcqueue" SET "user" = NULL WHERE "user" = 0;
ALTER TABLE "tcqueue" ALTER COLUMN "user" TYPE integer;
ALTER TABLE "tcqueue" ALTER COLUMN "user" SET DEFAULT NULL;
ALTER TABLE "tcqueue" ADD CONSTRAINT "tcqueue_fk_user" FOREIGN KEY ("user") REFERENCES "accounts" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;

UPDATE "tcqueuehistory" SET "user" = NULL WHERE "user" = 0;
ALTER TABLE "tcqueuehistory" ALTER COLUMN "user" TYPE integer;
ALTER TABLE "tcqueuehistory" ALTER COLUMN "user" SET DEFAULT NULL;
ALTER TABLE "tcqueuehistory" ADD CONSTRAINT "tcqueuehistory_fk_user" FOREIGN KEY ("user") REFERENCES "accounts" ("id") ON UPDATE CASCADE ON DELETE RESTRICT;

# --- !Downs

