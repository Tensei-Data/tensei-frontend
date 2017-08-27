# --- !Ups

CREATE TABLE "tcqueuehistory"
(
  "uuid" character varying(64) NOT NULL,
  "tkid" bigint NOT NULL,
  "created" timestamp NOT NULL DEFAULT now(),
  "finished" timestamp DEFAULT null,
  "cron" boolean NOT NULL DEFAULT false,
  "trigger" boolean NOT NULL DEFAULT false,
  "user" bigint DEFAULT 0,
  "completed" boolean NOT NULL DEFAULT false,
  "aborted" boolean NOT NULL DEFAULT false,
  "error" boolean NOT NULL DEFAULT false,
  CONSTRAINT "tcqueuehistory_pk" PRIMARY KEY ("uuid"),
  CONSTRAINT "tcqueuehistory_fk_tkid" FOREIGN KEY ("tkid") REFERENCES "transformationconfigurations" ("id")  ON UPDATE CASCADE ON DELETE CASCADE
);

# --- !Downs

DROP TABLE "tcqueuehistory"