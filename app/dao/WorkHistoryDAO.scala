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

import models.{ ChartDataEntry, Permission, WorkHistoryEntry, WorkHistoryStatistics }
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
  * A data access object (DAO) for managing the history table of transformation configuration runs.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class WorkHistoryDAO @Inject()(override protected val configuration: Configuration,
                               override protected val dbConfigProvider: DatabaseConfigProvider)
    extends Tables(configuration, dbConfigProvider) {
  import profile.api._

  /**
    * Load all entries of the queue history.
    *
    * @return A future holding a list of history entries.
    */
  def all: Future[Seq[WorkHistoryEntry]] =
    dbConfig.db.run(workHistory.sortBy(_.created.desc).result)

  /**
    * Load all entries from the history queue that are not yet finished.
    *
    * @return A future holding a list of history entries.
    */
  def allActive: Future[Seq[WorkHistoryEntry]] =
    dbConfig.db.run(workHistory.filter(_.finished.isEmpty).sortBy(_.created.asc).result)

  /**
    * Load all entries from the history queue that are finished.
    *
    * @param max The maximum number of entries to return.
    * @return A future holding a list of history entries.
    */
  def allFinished(max: Int): Future[Seq[WorkHistoryEntry]] =
    dbConfig.db.run(
      workHistory.filter(_.finished.isDefined).sortBy(_.finished.desc).take(max).result
    )

  /**
    * Return all history entries that are readable by the user.
    * For this the transformation configurations table is joined in and used
    * to check access permissions of the related configurations.
    *
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @param context An implicit execution context.
    * @return A future holding a list of history entries.
    */
  def allReadable(ownerId: Int, groupIds: Set[Int])(
      implicit context: ExecutionContext
  ): Future[Seq[WorkHistoryEntry]] = {
    val baseQuery = workHistory.withTransformationConfig.filter(
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
      .run(query.sortBy(_._1.created.asc).result)
      .map(
        rows => rows.map(_._1)
      )
  }

  /**
    * Return all history entries that are readable by the user regarding the
    * given limits. This is needed for paginating the history.
    * For this the transformation configurations table is joined in and used
    * to check access permissions of the related configurations.
    *
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @param skip The number of entries that shall be omitted.
    * @param size The maximum number of entries that shall be returned.
    * @param context An implicit execution context.
    * @return A future holding a list of history entries.
    */
  def allReadableWithLimits(ownerId: Int, groupIds: Set[Int])(skip: Int, size: Int)(
      implicit context: ExecutionContext
  ): Future[Seq[WorkHistoryEntry]] = {
    val baseQuery = workHistory.withTransformationConfig.filter(
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
      .run(query.sortBy(_._1.started.desc).drop(skip).take(size).result)
      .map(
        rows => rows.map(_._1)
      )
  }

  /**
    * Calculate some statistics regarding the history queue. The provided user id
    * and group ids are used to calculate only statistics for entries that the
    * user is permitted to see.
    *
    * As many computations as possible are done directly in the database queries. Some however
    * have to be computed in memory.
    *
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @param context An implicit execution context.
    * @return A future holding the statistics.
    */
  def calculateStatistics(ownerId: Int, groupIds: Set[Int])(
      implicit context: ExecutionContext
  ): Future[WorkHistoryStatistics] = {

    /**
      * A helper function to map the running time (duration) of a history entry
      * to a key.
      *
      * @param d The duration holding the running time.
      * @return The appropriate mapping key.
      */
    def groupDurations(d: java.time.Duration): String =
      d.getSeconds match {
        case x if x < 20   => "SECONDS"
        case x if x < 60   => "MINUTE"
        case x if x < 3600 => "MINUTES"
        case x if x < 7200 => "HOUR"
        case _             => "HOURS"
      }

    val baseQuery = workHistory.withTransformationConfig.filter(
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
    val startedByCron    = query.filter(_._1.cron).length.result
    val startedByTrigger = query.filter(_._1.trigger).length.result
    val startedByUser    = query.filter(_._1.user.isDefined).length.result
    val aborted          = query.filter(_._1.aborted).length.result
    val completed        = query.filter(_._1.completed).length.result
    val failed           = query.filter(_._1.error).length.result
    val durations = query
      .filter(r => r._1.started.isDefined && r._1.finished.isDefined)
      .map(r => (r._1.started, r._1.finished))
      .result
    for {
      cronCount     <- dbConfig.db.run(startedByCron)
      triggerCount  <- dbConfig.db.run(startedByTrigger)
      userCount     <- dbConfig.db.run(startedByUser)
      abortCount    <- dbConfig.db.run(aborted)
      completeCount <- dbConfig.db.run(completed)
      failCount     <- dbConfig.db.run(failed)
      ds            <- dbConfig.db.run(durations)
    } yield {
      val exitStatusStats = List(
        ChartDataEntry(label = "COMPLETED", value = completeCount),
        ChartDataEntry(label = "ABORTED", value = abortCount),
        ChartDataEntry(label = "ERROR", value = failCount)
      )
      val startedByStats = List(
        ChartDataEntry(label = "CRON", value = cronCount),
        ChartDataEntry(label = "TRIGGER", value = triggerCount),
        ChartDataEntry(label = "USER", value = userCount)
      )
      val durationStats = ds
        .flatMap(
          e =>
            e._1.fold(None: Option[java.time.Duration])(
              started =>
                e._2.fold(None: Option[java.time.Duration])(
                  finished =>
                    Option(
                      java.time.Duration.of(finished.getTime - started.getTime,
                                            java.time.temporal.ChronoUnit.MILLIS)
                  )
              )
          )
        )
        .groupBy(e => groupDurations(e))
        .mapValues(_.size)
        .map(e => ChartDataEntry(label = e._1, value = e._2))
        .toList
      WorkHistoryStatistics(
        exitStatus = exitStatusStats,
        runningTime = durationStats,
        startedBy = startedByStats
      )
    }
  }

  /**
    * Count the number of entries in the history table.
    *
    * @return A future holding the number of entries.
    */
  def count: Future[Int] = dbConfig.db.run(workHistory.length.result)

  /**
    * Return the number of history entries that are readable by the user.
    * For this the transformation configurations table is joined in and used
    * to check access permissions of the related configurations.
    *
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @return A future holding the number of history entries.
    */
  def countReadable(ownerId: Int, groupIds: Set[Int]): Future[Int] = {
    val baseQuery = workHistory.withTransformationConfig.filter(
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
    dbConfig.db.run(query.length.result)
  }

  /**
    * Create the given history queue entry in the database.
    *
    * @param e The entry to be created.
    * @return A future holding the created entry.
    */
  def create(e: WorkHistoryEntry): Future[Try[WorkHistoryEntry]] =
    dbConfig.db.run(
      (workHistory += e).andThen(workHistory.filter(_.uuid === e.uuid).result.head).asTry
    )

  /**
    * Analyse the given set of entry ids (uuids) regarding the given user id and groups.
    * The function returns only the uuids from the given set that are readable by the user.
    *
    * @param uuids A set of uuids of existing history entries.
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @return A future holding a list of uuids allowed to be read by the user.
    */
  def filterReadable(uuids: Set[String], ownerId: Int, groupIds: Set[Int]): Future[Seq[String]] = {
    val baseQuery = workHistory
      .filter(_.uuid.inSetBind(uuids))
      .withTransformationConfig
      .filter(
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
    dbConfig.db.run(query.map(_._1.uuid).distinct.result)
  }

  /**
    * Return the history entry for the given uuid.
    *
    * @param uuid The uuid for the entry.
    * @return A future holding an option to the history entry.
    */
  def findById(uuid: String): Future[Option[WorkHistoryEntry]] =
    dbConfig.db.run(workHistory.filter(_.uuid === uuid).result.headOption)

  /**
    * Return the history entry for the given uuid that is not yet finished.
    *
    * @param uuid The uuid for the entry.
    * @return A future holding an option to the history entry.
    */
  def findRunningById(uuid: String): Future[Option[WorkHistoryEntry]] =
    dbConfig.db.run(
      workHistory.filter(_.uuid === uuid).filterNot(_.finished.isDefined).result.headOption
    )

  /**
    * Return the last finished work history entry for each transformation configuration if applicable.
    *
    * @param max An optional value that restricts the maximum number of entries to return.
    * @return A future holding a list of history entries.
    */
  def lastFinished(max: Option[Int]): Future[Seq[WorkHistoryEntry]] = {
    val q = workHistory
      .filter(_.finished.isDefined)
      .sortBy(_.tkid)
      .sortBy(_.finished.desc)
      .distinctOn(_.tkid) // FIXME `distinctOn` doesn't seem to work in this case with Slick!
    max.fold(dbConfig.db.run(q.result))(m => dbConfig.db.run(q.take(m).result))
  }

  /**
    * Update the given history queue entry in the database.
    *
    * @param e The entry that shall be updated.
    * @return A future holding the number of affected database rows.
    */
  def update(e: WorkHistoryEntry): Future[Int] =
    dbConfig.db.run(workHistory.filter(_.uuid === e.uuid).update(e))

}
