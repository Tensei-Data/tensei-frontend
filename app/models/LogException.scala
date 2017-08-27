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
  * A logged exception.
  *
  * @param event_id The event id from the log.
  * @param lines    The log lines for the exception.
  */
case class LogException(event_id: Long, lines: Seq[LogExceptionLine])

/**
  * A log line for an exception.
  *
  * @param i          The line number.
  * @param trace_line The text from the log line.
  */
case class LogExceptionLine(i: Short, trace_line: String)
