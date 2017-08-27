# --- !Ups

ALTER TABLE "accounts" ADD COLUMN "gid" BIGINT;
ALTER TABLE "accounts" ADD COLUMN "disabled" BOOLEAN;

# --- !Downs

ALTER TABLE "accounts" DROP COLUMN "gid";
ALTER TABLE "accounts" DROP COLUMN "disabled";
