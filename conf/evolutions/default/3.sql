# --- !Ups

CREATE TABLE "accountgroupmappings"
(
  "uid" bigint NOT NULL,
  "gid" bigint NOT NULL,
  CONSTRAINT "accountgroupmappings_pk" PRIMARY KEY ("uid", "gid"),
  CONSTRAINT "accountgroupmappings_fk_gid" FOREIGN KEY ("gid") REFERENCES "usergroups" ("id") ON UPDATE RESTRICT ON DELETE CASCADE,
  CONSTRAINT "accountgroupmappings_fk_uid" FOREIGN KEY ("uid") REFERENCES "accounts" ("id") ON UPDATE RESTRICT ON DELETE CASCADE
);

# --- !Downs

DROP TABLE "accountgroupmappings";