/*
 * Copyright (C) 2014 - 2017  Contributors as noted in the AUTHORS.md file
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package models

/**
  * A helper for the log entries.
  *
  * @param event_id      The unique identifier of the log entry.
  * @param timestamp     The timestamp when the entry was created.
  * @param message       The log message.
  * @param logger_name   The name of the entity that created the log entry.
  * @param level         The severity level of the log entry.
  * @param hostname      The hostname of the entity from where the log entry was made.
  * @param uuid          The uuid of the transformation configuration.
  */
case class LogEntry(event_id: Long,
                    timestamp: Long,
                    message: String,
                    logger_name: String,
                    level: String,
                    hostname: Option[String] = None,
                    uuid: Option[String] = None)
