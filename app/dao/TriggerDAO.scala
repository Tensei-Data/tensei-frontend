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

import models.{ Permission, Trigger }
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future
import scala.util.Try

/**
  * A data access object (DAO) for handling triggers.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class TriggerDAO @Inject()(override protected val configuration: Configuration,
                           override protected val dbConfigProvider: DatabaseConfigProvider)
    extends Tables(configuration, dbConfigProvider) {
  import profile.api._

  /**
    * Return all triggers from the database.
    *
    * @return A future holding a list of triggers.
    */
  def all: Future[Seq[Trigger]] = dbConfig.db.run(triggers.result)

  /**
    * Return all active triggers from the database.
    *
    * @return A future holding a list of triggers.
    */
  def allActive: Future[Seq[Trigger]] = dbConfig.db.run(triggers.filter(_.active).result)

  /**
    * Return all triggers that are readable by the user.
    *
    * @param ownerId The id of the user.
    * @param groupIds A list of group ids.
    * @return A future holding the list of all readable triggers.
    */
  def allReadable(ownerId: Int, groupIds: Set[Int]): Future[Seq[Trigger]] = {
    val baseQuery = triggers.filter(
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
    * Return the number of entries in the triggers table.
    *
    * @return A future holding the number of triggers in the database.
    */
  def count: Future[Int] = dbConfig.db.run(triggers.length.result)

  /**
    * Create the given trigger in the database.
    *
    * @param t The trigger to be created.
    * @return A future holding the created trigger.
    */
  def create(t: Trigger): Future[Try[Trigger]] =
    dbConfig.db.run(
      ((triggers returning triggers
        .map(_.id) into ((trigger, id) => trigger.copy(id = Option(id)))) += t).asTry
    )

  /**
    * Destroy the given trigger in the database.
    *
    * @param t The trigger to be destroyed.
    * @return A future holding the number of affected database rows.
    */
  def destroy(t: Trigger): Future[Int] =
    t.id.fold(Future.successful(0))(id => dbConfig.db.run(triggers.filter(_.id === id).delete))

  /**
    * Try to find the trigger with the given ID which must be active.
    *
    * @param id The database ID of the trigger.
    * @return A future holding an option to the trigger.
    */
  def findActiveById(id: Long): Future[Option[Trigger]] =
    dbConfig.db.run(triggers.filter(r => r.id === id && r.active).result.headOption)

  /**
    * Try to find the trigger with the given ID.
    *
    * @param id The database ID of the trigger.
    * @return A future holding an option to the trigger.
    */
  def findById(id: Long): Future[Option[Trigger]] =
    dbConfig.db.run(triggers.filter(_.id === id).result.headOption)

  /**
    * Update the given trigger in the database.
    *
    * @param t The trigger to be updated.
    * @return A future holding the number of affected database rows.
    */
  def update(t: Trigger): Future[Int] =
    t.id.fold(Future.successful(0))(id => dbConfig.db.run(triggers.filter(_.id === id).update(t)))

}
