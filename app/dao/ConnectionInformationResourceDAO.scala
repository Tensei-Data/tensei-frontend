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

import models.{ ConnectionInformationResource, Permission }
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future
import scala.util.Try

/**
  * A data access object (DAO) for handling connection information resources.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class ConnectionInformationResourceDAO @Inject()(
    override protected val configuration: Configuration,
    override protected val dbConfigProvider: DatabaseConfigProvider
) extends Tables(configuration, dbConfigProvider) {
  import driver.api._

  /**
    * Returns a list of all connection information resources that are readable
    * by the user.
    *
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @return A future holding a list of connection information resources.
    */
  def allReadable(ownerId: Int, groupIds: Set[Int]): Future[Seq[ConnectionInformationResource]] = {
    val baseQuery = connectioninformationresources.filter(
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
    dbConfig.db.run(query.sortBy(_.uri.asc).result)
  }

  /**
    * Create the given connection information resource in the database.
    *
    * @param c A connection information resource.
    * @return A future holding the created resource.
    */
  def create(c: ConnectionInformationResource): Future[Try[ConnectionInformationResource]] =
    dbConfig.db.run(
      ((connectioninformationresources returning connectioninformationresources
        .map(_.id) into ((res, id) => res.copy(id = Option(id)))) += c).asTry
    )

  /**
    * Delete the given connection information resource from the database.
    *
    * @param c The connection information resource to be deleted.
    * @return A future holding the number of affected database rows.
    */
  def destroy(c: ConnectionInformationResource): Future[Int] =
    dbConfig.db.run(connectioninformationresources.filter(_.id === c.id).delete)

  /**
    * Load the connection information resource with the given ID from the database.
    *
    * @param id The unique database ID of the resource.
    * @return A future holding an option to the resource.
    */
  def findById(id: Long): Future[Option[ConnectionInformationResource]] =
    dbConfig.db.run(connectioninformationresources.filter(_.id === id).result.headOption)

  /**
    * Load the connection information resources with the given ids from the database.
    *
    * @param ids A list of resource IDs.
    * @return A future holding a list of connection resources.
    */
  def findByIds(ids: Set[Long]): Future[Seq[ConnectionInformationResource]] =
    dbConfig.db.run(connectioninformationresources.filter(_.id inSetBind ids).result)

  /**
    * Load all connection information resources that are connected to the given cookbook
    * and dfasdl id.
    *
    * @param cookbookId The ID of the cookbook.
    * @param dfasdlId The ID of the DFASDL.
    * @return A future holding a list of connection information resources.
    */
  def findByCokbookAndDfasdlId(cookbookId: String,
                               dfasdlId: String): Future[Seq[ConnectionInformationResource]] =
    dbConfig.db.run(
      connectioninformationresources
        .filter(e => e.cookbook_id === cookbookId && e.dfasdl_id === dfasdlId)
        .result
    )

  /**
    * Load all connection information resources the are connected to the given
    * cookbook.
    *
    * @param cookbookId The ID of the cookbook.
    * @return A future holding a list of connection information resources.
    */
  def findByCokbookId(cookbookId: String): Future[Seq[ConnectionInformationResource]] =
    dbConfig.db.run(connectioninformationresources.filter(e => e.cookbook_id === cookbookId).result)

  /**
    * Update the given connection information resource in the database.
    *
    * @param c A connection information resource.
    * @return A future holding the number of affected database rows.
    */
  def update(c: ConnectionInformationResource): Future[Int] =
    c.id.fold(Future.successful(0))(
      id => dbConfig.db.run(connectioninformationresources.filter(_.id === id).update(c))
    )
}
