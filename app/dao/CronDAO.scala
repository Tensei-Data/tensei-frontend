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

import models.{ Cron, Permission }
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future
import scala.util.Try

/**
  * A data access object (DAO) for handling crontab entries.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class CronDAO @Inject()(override protected val configuration: Configuration,
                        override protected val dbConfigProvider: DatabaseConfigProvider)
    extends Tables(configuration, dbConfigProvider) {
  import profile.api._

  /**
    * Return all crontab entries that are readable by the user.
    *
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @return A future holding a list of crontab entries.
    */
  def allReadable(ownerId: Int, groupIds: Set[Int]): Future[Seq[Cron]] = {
    val baseQuery = crontab.filter(
      row =>
        row.ownerId === ownerId || row.worldPermissions
          .inSetBind(Permission.ReadablePermissionSets)
    )
    val query =
      if (groupIds.nonEmpty)
        baseQuery.filter(
          row =>
            row.groupId.inSetBind(groupIds) && row.groupPermissions
              .inSetBind(Permission.ReadablePermissionSets)
        )
      else
        baseQuery
    dbConfig.db.run(query.result)
  }

  /**
    * Load all active crontab entries from the database that have a connected
    * transformation configuration.
    *
    * @return A future holding a list of crontab entries.
    */
  def allActive: Future[Seq[Cron]] =
    dbConfig.db.run(crontab.filter(_.active).result)

  /**
    * Return the number of crontab entries from the database.
    *
    * @return A future holding the number of entries.
    */
  def count: Future[Int] =
    dbConfig.db.run(crontab.length.result)

  /**
    * Create the given crontab entry in the database.
    *
    * @param c A crontab entry.
    * @return A future holding the created crontab entry.
    */
  def create(c: Cron): Future[Try[Cron]] =
    dbConfig.db.run(
      (crontab returning crontab.map(_.id) into ((e, id) => e.copy(id = Option(id))) += c).asTry
    )

  /**
    * Destroy the given crontab entry in the database.
    *
    * @param c A crontab entry.
    * @return A future holding the number of affected database rows.
    */
  def destroy(c: Cron): Future[Int] =
    c.id.fold(Future.successful(0))(id => dbConfig.db.run(crontab.filter(_.id === id).delete))

  /**
    * Load the crontab entry with the given ID from the database.
    *
    * @param id The database ID of the crontab entry.
    * @return A future holding an option to the crontab entry.
    */
  def findById(id: Long): Future[Option[Cron]] =
    dbConfig.db.run(crontab.filter(_.id === id).result.headOption)

  /**
    * Update the given crontab entry in the database.
    *
    * @param c A crontab entry.
    * @return A future holding the number of affected database rows.
    */
  def update(c: Cron): Future[Int] =
    c.id.fold(Future.successful(0))(id => dbConfig.db.run(crontab.filter(_.id === id).update(c)))
}
