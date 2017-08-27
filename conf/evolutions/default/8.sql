# --- !Ups

CREATE TABLE "cookbooks"
(
  "id" character varying(254) NOT NULL,
  "cookbook" text,
  CONSTRAINT "cookbooks_pk" PRIMARY KEY ("id")
);

ALTER TABLE "cookbookresources" DROP COLUMN "cookbook";
ALTER TABLE "cookbookresources" ADD COLUMN "cookbook_id" character varying(254) NOT NULL;
ALTER TABLE "cookbookresources" ADD CONSTRAINT "cookbookresources_fk_cookbooks" FOREIGN KEY ("cookbook_id") REFERENCES "cookbooks" ("id") ON UPDATE CASCADE ON DELETE CASCADE;

# --- !Downs

ALTER TABLE "cookbookresources" DROP CONSTRAINT "cookbookresources_fk_cookbooks";
ALTER TABLE "cookbookresources" DROP COLUMN "cookbook_id";
ALTER TABLE "cookbookresources" ADD COLUMN "cookbook" TEXT;
DROP TABLE "cookbooks";