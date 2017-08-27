# --- !Ups

CREATE TABLE "agent_run_logs" (
  "uuid"        CHARACTER VARYING(64) NOT NULL,
  "line_offset" BIGINT                NOT NULL,
  "line"        TEXT                  NOT NULL,
  CONSTRAINT "agent_run_logs_pk" PRIMARY KEY ("uuid", "line_offset"),
  CONSTRAINT "agent_run_logs_fk" FOREIGN KEY ("uuid") REFERENCES "tcqueuehistory" ("uuid") ON UPDATE CASCADE ON DELETE CASCADE
);

# --- !Downs

DROP TABLE IF EXISTS "agent_run_logs";
