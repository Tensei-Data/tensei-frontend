#!/usr/bin/env bash
# This script initialises the production configuration for the Tensei-Data
# frontend.

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 path-to-config-file [create-postgres-db]"
  echo "If you specify the 'create-postgres-db' flag the script will create a postgres user and database."
  exit 1
fi

CFG="$1"
PGS="$2"

if [[ -f "${CFG}" ]]; then
  echo "Configuration file already exists at ${CFG}."
else
  FRONTEND_DB_USER="tensei"
  FRONTEND_DB_PASS=`pwgen -cns 32`
  FRONTEND_DB_FILE_PASS=`pwgen -cns 32`
  if [[ "${PGS}" == "create-postgres-db" ]]; then
    echo "Creating frontend database user..."
    sudo -u postgres psql -c "CREATE ROLE ${FRONTEND_DB_USER} WITH CREATEDB LOGIN ENCRYPTED PASSWORD '${FRONTEND_DB_PASS}'" || exit 1
    echo "Creating frontend database..."
    sudo -u postgres psql -c "CREATE DATABASE tenseifrontend WITH OWNER ${FRONTEND_DB_USER}" || exit 1
  fi
  echo "Creating configuration file at ${CFG}."
  cat >"${CFG}"<<EOF
### Configuration file of Tensei-Data frontend for running in production mode.
### Never remove the following line!
include "application.conf"

### If you change this all connected users will have to re-authorise.
play.crypto.secret=`pwgen -cns 128`

### Database settings for the default embedded database.

# If you adjust the connection url please don't remove the option "MODE"
# or things will break!
#
# To specify a custom path you may use something like this:
# "jdbc:h2:/home/tensei/db/frontend;CIPHER=AES;MODE=PostgreSQL"
#
slick.dbs.default.db.url      = "jdbc:h2:"\${defaults.slick.dbs.default.path}"/.database/tenseifrontend;CIPHER=AES;MODE=PostgreSQL"

# These are two passwords, therefore you must not use spaces in them.
# The first password is for the AES encrypted db file and the second one for
# the database connection.
# If you decide to drop the "CIPHER=AES" part from the connection url then
# you must only specify one password here.
slick.dbs.default.db.password = "${FRONTEND_DB_FILE_PASS} ${FRONTEND_DB_PASS}"

### Settings for a using a PostgreSQL database.

# Uncomment and adjust this section if you want to use a PostgreSQL database.
# Please note that data in the embedded database will not be transfered!
# Remember to comment the section above for the embedded database if you do this.

#slick.dbs.default.driver      = "slick.driver.PostgresDriver$"
#slick.dbs.default.db.driver   = "org.postgresql.Driver"

# You can adjust the connection url and user and password to your needs below.
#slick.dbs.default.db.url      = "jdbc:postgresql://127.0.0.1:5432/tenseifrontend"
#slick.dbs.default.db.user     = "tensei"
#slick.dbs.default.db.password = "${FRONTEND_DB_PASS}"

EOF
  echo "Done."
fi
