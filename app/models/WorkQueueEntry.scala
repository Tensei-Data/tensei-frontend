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

import argonaut._, Argonaut._

import java.sql.Timestamp
import java.time.LocalDateTime

/**
  * The entries of the queue that holds unprocessed transformation configurations.
  *
  * @param uuid        An Universally Unique Identifier.
  * @param tkid        The associated Transformation configuration ID.
  * @param sortorder   The position in the list of unprocessed transformation configurations.
  * @param created     When the entry was added to the queue.
  * @param cron        Whether the entry was added by a cron.
  * @param trigger     Whether the entry was added by a trigger.
  * @param user        Whether the entry was added by an user.
  */
final case class WorkQueueEntry(
    uuid: String,
    tkid: Long,
    sortorder: Int,
    created: Timestamp,
    cron: Boolean,
    trigger: Boolean,
    user: Option[Int]
)

object WorkQueueEntry {

  // The default value for the sort order.
  val DEFAULT_SORT_ORDER = Int.MaxValue

  /**
    * Use this function to create an entry using default values marked with
    * the `cron` flag.
    *
    * @param tcId The ID of a transformation configuration.
    * @return A transformation config queue entry with the cron flag set.
    */
  def fromCron(tcId: Long): WorkQueueEntry = fromDefaults(tcId).copy(cron = true)

  /**
    * Generate an entry from the default values.
    *
    * @param tcId The ID of a transformation configuration.
    * @return A transformation config queue entry.
    */
  def fromDefaults(tcId: Long): WorkQueueEntry = WorkQueueEntry(
    uuid = generateId,
    tkid = tcId,
    sortorder = DEFAULT_SORT_ORDER,
    created = java.sql.Timestamp.valueOf(LocalDateTime.now()),
    cron = false,
    trigger = false,
    user = None
  )

  /**
    * Use this function to create an entry using default values marked with
    * the `trigger` flag.
    *
    * @param tcId The ID of a transformation configuration.
    * @return A transformation config queue entry with the trigger flag set.
    */
  def fromTrigger(tcId: Long): WorkQueueEntry = fromDefaults(tcId).copy(trigger = true)

  /**
    * Use this function to create an entry using default values marked with
    * the user id that created the entry.
    *
    * @param tcId The ID of a transformation configuration.
    * @param userId The ID of the user that created the entry.
    * @return A transformation config queue entry.
    */
  def fromUser(tcId: Long, userId: Int): WorkQueueEntry =
    fromDefaults(tcId).copy(user = Option(userId))

  /**
    * Generate an ID for the transformation configuration queue entry.
    *
    * @return A randomly generated UUID which is supposed to be unique.
    */
  def generateId: String = java.util.UUID.randomUUID().toString

  /**
    * An argonaut codec for converting a transformation configuration queue entry from and
    * to json.
    *
    * @return An argonaut codec for [[WorkQueueEntry]] instances.
    */
  implicit def TransformationConfigQueueEntryCodec: CodecJson[WorkQueueEntry] =
    CodecJson(
      (e: WorkQueueEntry) =>
        ("uuid" := e.uuid) ->: ("tkid" := e.tkid) ->: ("sortorder" := e.sortorder)
        ->: ("created" := e.created) ->: ("cron" := e.cron) ->: ("trigger" := e.trigger) ->: ("user" := e.user) ->: jEmptyObject,
      c =>
        for {
          uuid            <- (c --\ "uuid").as[String]
          tkid            <- (c --\ "tkid").as[Long]
          sort            <- (c --\ "sortorder").as[Int]
          created         <- (c --\ "created").as[Timestamp]
          isCron          <- (c --\ "cron").as[Boolean]
          isTrigger       <- (c --\ "trigger").as[Boolean]
          triggeredByUser <- (c --\ "user").as[Option[Int]]
        } yield
          WorkQueueEntry(
            uuid = uuid,
            tkid = tkid,
            sortorder = sort,
            created = created,
            cron = isCron,
            trigger = isTrigger,
            user = triggeredByUser
        )
    )
}
