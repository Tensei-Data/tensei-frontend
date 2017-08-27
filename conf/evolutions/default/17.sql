# --- !Ups

ALTER TABLE "tcqueuehistory" ADD COLUMN "message" character varying(255);

# --- !Downs

ALTER TABLE "tcqueuehistory" DROP COLUMN "message";
