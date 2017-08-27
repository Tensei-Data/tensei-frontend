# --- !Ups

ALTER TABLE "connectioninformations" ADD COLUMN "language_tag"  character varying(50);
COMMENT ON COLUMN "connectioninformations"."language_tag" IS 'Language tag for the source that represents a Locale.';

# --- !Downs

ALTER TABLE "connectioninformations" DROP COLUMN "language_tag";