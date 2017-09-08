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

package dao

import javax.inject.Inject

import models.{ Permission, WorkQueueEntry }
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
  * A data access object (DAO) for handling anything related to managing the
  * queue of transformation configurations.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class WorkQueueDAO @Inject()(override protected val configuration: Configuration,
                             override protected val dbConfigProvider: DatabaseConfigProvider)
    extends Tables(configuration, dbConfigProvider) {
  import profile.api._

  /**
    * Load all entries of the transformation configuration queue from the database.
    *
    * @return A future holding a list of transformation config queue entries.
    */
  def all: Future[Seq[WorkQueueEntry]] =
    dbConfig.db.run(workQueue.sortBy(_.sortorder.asc).result)

  /**
    * Return all queue entries that are readable by the user.
    * For this the transformation configurations table is joined in and used
    * to check access permissions of the related configurations.
    *
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @param context An implicit execution context.
    * @return A future holding a list of queue entries.
    */
  def allReadable(ownerId: Int, groupIds: Set[Int])(
      implicit context: ExecutionContext
  ): Future[Seq[WorkQueueEntry]] = {
    val baseQuery = workQueue.withTransformationConfig.filter(
      row =>
        row._2.ownerId === ownerId || row._2.worldPermissions
          .inSetBind(Permission.ReadablePermissionSets)
    )
    val query =
      if (groupIds.nonEmpty)
        baseQuery.filter(
          row =>
            row._2.groupId.inSetBind(groupIds) && row._2.groupPermissions
              .inSetBind(Permission.ReadablePermissionSets)
        )
      else
        baseQuery
    dbConfig.db
      .run(query.sortBy(_._1.sortorder.asc).result)
      .map(
        rows => rows.map(_._1)
      )
  }

  /**
    * Create the given transformation configuration queue entry in the database.
    *
    * @param e The entry for the transformation configuration queue.
    * @return A future holding the created entry.
    */
  def create(e: WorkQueueEntry): Future[Try[WorkQueueEntry]] =
    dbConfig.db.run(
      (workQueue += e).andThen(workQueue.filter(_.uuid === e.uuid).result.head).asTry
    )

  /**
    * Destroy the given transformation configuration queue entry in the database.
    *
    * @param e The queue entry to be deleted.
    * @return A future holding the number of affected database rows.
    */
  def destroy(e: WorkQueueEntry): Future[Int] =
    dbConfig.db.run(workQueue.filter(_.uuid === e.uuid).delete)

  /**
    * Destroy the transformation config queue entry with the given uuid in
    * the database.
    *
    * @param uuid The UUID of the queue entry.
    * @return A future holding the number of affected database rows.
    */
  def destroy(uuid: String): Future[Int] =
    dbConfig.db.run(workQueue.filter(_.uuid === uuid).delete)

  /**
    * Return the transformation configuration queue entry with the given
    * ID.
    *
    * @param uuid The UUID of the queue entry.
    * @return A future holding an option to the queue entry.
    */
  def findById(uuid: String): Future[Option[WorkQueueEntry]] =
    dbConfig.db.run(workQueue.filter(_.uuid === uuid).result.headOption)

  /**
    * Return the next logical entry from the queue. The queue is sorted by the
    * sortorder field and the timestamp when the entry was created and the first
    * entry is then returned.
    *
    * @return A future holding an option to the queue entry.
    */
  def getNextEntry: Future[Option[WorkQueueEntry]] =
    dbConfig.db.run(workQueue.sortBy(_.sortorder.asc).sortBy(_.created.asc).result.headOption)

  /**
    * Update the given transformation configuration queue entry in the database.
    *
    * @param e The queue entry to be updated.
    * @return A future holding the number of affected database rows.
    */
  def update(e: WorkQueueEntry): Future[Int] =
    dbConfig.db.run(workQueue.filter(_.uuid === e.uuid).update(e))

}
