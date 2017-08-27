# --- !Ups

INSERT INTO "usergroups" ("name") VALUES('admins');
INSERT INTO "usergroups" ("name") VALUES('users');

# --- !Downs

DELETE FROM "usergroups" WHERE "name" = 'admins';
DELETE FROM "usergroups" WHERE "name" = 'users';
