# --- !Ups

CREATE TABLE "tcqueue"
(
  "id" bigserial NOT NULL,
  "tkid" bigint NOT NULL,
  "sortorder" integer DEFAULT 2147483646,
  "created" timestamp NOT NULL DEFAULT NOW(),
  "cron" boolean NOT NULL DEFAULT false,
  "trigger" boolean NOT NULL DEFAULT false,
  "user" bigint DEFAULT 0,
  CONSTRAINT "tcqueue_pk" PRIMARY KEY ("id"),
  CONSTRAINT "tcqueue_fk_tkid" FOREIGN KEY ("tkid") REFERENCES "transformationconfigurations" ("id")  ON UPDATE CASCADE ON DELETE CASCADE
);

# --- !Downs

DROP TABLE "tcqueue"