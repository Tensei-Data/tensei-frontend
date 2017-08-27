# --- !Ups

ALTER TABLE "triggers" ADD COLUMN "trigger_tkid" bigint DEFAULT 0;
ALTER TABLE "triggers" ALTER COLUMN "endpoint_uri" DROP NOT NULL;

# --- !Downs

ALTER TABLE "triggers" DROP COLUMN "trigger_tkid";
ALTER TABLE "triggers" ALTER COLUMN "endpoint_uri" SET NOT NULL;
