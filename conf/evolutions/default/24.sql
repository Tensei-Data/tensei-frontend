# --- !Ups

ALTER TABLE "accounts" ADD COLUMN "watched_intro" boolean DEFAULT FALSE;

# --- !Downs

ALTER TABLE "accounts" DROP COLUMN "watched_intro";
