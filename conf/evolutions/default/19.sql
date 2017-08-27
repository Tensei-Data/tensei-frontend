# --- !Ups

ALTER TABLE "accounts" ADD CONSTRAINT "accounts_unique_name" UNIQUE ("name");

# --- !Downs

ALTER TABLE "accounts" DROP CONSTRAINT "accounts_unique_name";
