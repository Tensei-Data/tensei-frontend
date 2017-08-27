# --- !Ups

ALTER TABLE "tcqueue" DROP CONSTRAINT "tcqueue_pk";
ALTER TABLE "tcqueue" DROP COLUMN "id";
ALTER TABLE "tcqueue" ADD COLUMN "uuid" character varying(64) NOT NULL;
ALTER TABLE "tcqueue" ADD CONSTRAINT "tcqueue_pk" PRIMARY KEY ("uuid");

# --- !Downs

ALTER TABLE "tcqueue" DROP CONSTRAINT "tcqueue_pk";
ALTER TABLE "tcqueue" DROP COLUMN "uuid";
ALTER TABLE "tcqueue" ADD COLUMN "id" BIGSERIAL NOT NULL;
ALTER TABLE "tcqueue" ADD CONSTRAINT "tcqueue_pk" PRIMARY KEY ("id");
