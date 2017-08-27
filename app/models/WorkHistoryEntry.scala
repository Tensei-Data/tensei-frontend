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

import java.sql.Timestamp

/**
  * The entries of the queue that holds unprocessed transformation configurations.
  *
  * @param uuid        An Universally Unique Identifier.
  * @param tkid        The associated Transformation configuration ID.
  * @param created     When the entry was added to the queue.
  * @param started     When the entry was started by an agent.
  * @param finished    When the entry was finished by an agent.
  * @param cron        Whether the entry was added by a cron.
  * @param trigger     Whether the entry was added by a trigger.
  * @param user        Whether the entry was added by an user.
  * @param completed   Whether the entry is completed.
  * @param aborted     Whether the entry is aborted.
  * @param failed      Whether an error occurred.
  * @param message     Information String.
  */
final case class WorkHistoryEntry(
    uuid: String,
    tkid: Long,
    created: Timestamp,
    started: Option[Timestamp],
    finished: Option[Timestamp],
    cron: Boolean,
    trigger: Boolean,
    user: Option[Int],
    completed: Boolean,
    aborted: Boolean,
    failed: Boolean,
    message: Option[String]
)

object WorkHistoryEntry {

  /**
    * Create a history entry from a given transformation configuration queue entry.
    *
    * @param e A transformation configuration queue entry.
    * @return A history entry with sane defaults.
    */
  def fromQueueEntry(e: WorkQueueEntry): WorkHistoryEntry = WorkHistoryEntry(
    uuid = e.uuid,
    tkid = e.tkid,
    created = e.created,
    started = None,
    finished = None,
    cron = e.cron,
    trigger = e.trigger,
    user = e.user,
    completed = false,
    aborted = false,
    failed = false,
    message = None
  )

}
