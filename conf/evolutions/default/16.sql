# --- !Ups

ALTER TABLE "tcqueuehistory" ADD COLUMN "started" timestamp;

# --- !Downs

ALTER TABLE "tcqueuehistory" DROP COLUMN "started";
