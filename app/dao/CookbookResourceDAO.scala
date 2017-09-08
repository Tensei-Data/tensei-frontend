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

import com.wegtam.tensei.adt.Cookbook
import models.{ CookbookResource, Permission }
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
  * A data access object (DAO) for handling cookbook resources.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class CookbookResourceDAO @Inject()(
    override protected val configuration: Configuration,
    override protected val dbConfigProvider: DatabaseConfigProvider
) extends Tables(configuration, dbConfigProvider) {
  import profile.api._

  /**
    * Returns a list of all cookbook resources that are readable
    * by the user.
    *
    * The flag `loadCookbooks` can be set to `false` to speed up
    * operations when the actual cookbooks are not needed.
    *
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @param loadCookbooks A flag that indicates if the connected cookbooks should be included.
    * @return A future holding a list of cookbook resources.
    */
  def allReadable(ownerId: Int, groupIds: Set[Int])(
      loadCookbooks: Boolean
  )(implicit context: ExecutionContext): Future[Seq[CookbookResource]] = {
    val baseQuery = cookbookresources.filter(
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
    if (loadCookbooks) {
      dbConfig.db
        .run(query.withCookbook.result)
        .map(
          rs =>
            rs.map { row =>
              val res      = row._1
              val cookbook = row._2
              CookbookResource(
                id = res._1,
                cookbook = cookbook,
                ownerId = res._3,
                groupId = res._4,
                groupPermissions = res._5,
                worldPermissions = res._6
              )
          }
        )
    } else {
      dbConfig.db
        .run(query.sortBy(_.cookbook_id.asc).result)
        .map(
          rs =>
            rs.map { row =>
              val cookbook = Cookbook(
                id = row._2,
                sources = List.empty,
                target = None,
                recipes = List.empty
              )
              CookbookResource(
                id = row._1,
                cookbook = cookbook,
                ownerId = row._3,
                groupId = row._4,
                groupPermissions = row._5,
                worldPermissions = row._6
              )
          }
        )
    }
  }

  /**
    * Create the given cookbook resource in the database.
    *
    * @param c A cookbook resource.
    * @return A future holding the stored cookbook resource.
    */
  def create(
      c: CookbookResource
  )(implicit context: ExecutionContext): Future[Try[CookbookResource]] =
    dbConfig.db
      .run(
        (cookbooks += c.cookbook)
          .andThen(
            (cookbookresources returning cookbookresources.map(_.id)) += ((c.id,
                                                                           c.cookbook.id,
                                                                           c.ownerId,
                                                                           c.groupId,
                                                                           c.groupPermissions,
                                                                           c.worldPermissions))
          )
          .asTry
      )
      .map {
        case scala.util.Failure(t) => scala.util.Failure(t)
        case scala.util.Success(r) => scala.util.Success(c.copy(id = Option(r)))
      }

  /**
    * Destroy the given cookbook resource in the database.
    *
    * @param c A cookbook resource.
    * @return A future holding the number of affected database rows.
    */
  def destroy(c: CookbookResource): Future[Int] =
    c.id.fold(Future.successful(0))(
      id =>
        dbConfig.db.run(
          cookbookresources
            .filter(_.id === id)
            .delete
            .andThen(cookbooks.filter(_.id === c.cookbook.id).delete)
      )
    )

  /**
    * Load the cookbook resource with the given id from the database.
    *
    * @param id The database ID of the cookbook resource.
    * @param loadCookbook Indicates if the connected cookbook should be loaded or not.
    * @return A future holding an option to the cookbook resource.
    */
  def findById(
      id: Long
  )(loadCookbook: Boolean)(implicit context: ExecutionContext): Future[Option[CookbookResource]] =
    if (loadCookbook) {
      dbConfig.db
        .run(cookbookresources.filter(_.id === id).withCookbook.result.headOption)
        .map(
          o =>
            o.map { row =>
              val res      = row._1
              val cookbook = row._2
              CookbookResource(
                id = res._1,
                cookbook = cookbook,
                ownerId = res._3,
                groupId = res._4,
                groupPermissions = res._5,
                worldPermissions = res._6
              )
          }
        )
    } else {
      dbConfig.db
        .run(cookbookresources.filter(_.id === id).result.headOption)
        .map(
          o =>
            o.map { row =>
              val cookbook = Cookbook(
                id = row._2,
                sources = List.empty,
                target = None,
                recipes = List.empty
              )
              CookbookResource(
                id = row._1,
                cookbook = cookbook,
                ownerId = row._3,
                groupId = row._4,
                groupPermissions = row._5,
                worldPermissions = row._6
              )
          }
        )
    }

  /**
    * Load the first cookbook resource from the database that uses the
    * given cookbook ID.
    *
    * @param cookbookId The ID of a cookbook.
    * @param loadCookbook Indicates if the connected cookbook should be loaded or not.
    * @return A future holding an option to the cookbook resource.
    */
  def findByCookbookId(
      cookbookId: String
  )(loadCookbook: Boolean)(implicit context: ExecutionContext): Future[Option[CookbookResource]] =
    if (loadCookbook) {
      dbConfig.db
        .run(cookbookresources.filter(_.cookbook_id === cookbookId).withCookbook.result.headOption)
        .map(
          o =>
            o.map { row =>
              val res      = row._1
              val cookbook = row._2
              CookbookResource(
                id = res._1,
                cookbook = cookbook,
                ownerId = res._3,
                groupId = res._4,
                groupPermissions = res._5,
                worldPermissions = res._6
              )
          }
        )
    } else {
      dbConfig.db
        .run(cookbookresources.filter(_.cookbook_id === cookbookId).result.headOption)
        .map(
          o =>
            o.map { row =>
              val cookbook = Cookbook(
                id = row._2,
                sources = List.empty,
                target = None,
                recipes = List.empty
              )
              CookbookResource(
                id = row._1,
                cookbook = cookbook,
                ownerId = row._3,
                groupId = row._4,
                groupPermissions = row._5,
                worldPermissions = row._6
              )
          }
        )
    }

  /**
    * Update the given cookbook resource in the database.
    *
    * @param c A cookbook resource.
    * @return A future holding either an error or the updated resource.
    */
  def update(
      c: CookbookResource
  )(implicit context: ExecutionContext): Future[Try[CookbookResource]] =
    c.id match {
      case None => Future.successful(scala.util.Failure(new Error("Cookbookresource has no id!")))
      case Some(id) =>
        val row =
          (c.id, c.cookbook.id, c.ownerId, c.groupId, c.groupPermissions, c.worldPermissions)
        for {
          prevCookbookId <- dbConfig.db.run(
            cookbookresources.filter(_.id === id).map(_.cookbook_id).result.head
          )
          result <- dbConfig.db.run(
            cookbooks
              .filter(_.id === prevCookbookId)
              .update(c.cookbook)
              .andThen(cookbookresources.filter(_.id === c.id).update(row))
              .asTry
          )
        } yield
          result match {
            case scala.util.Failure(t) => scala.util.Failure(t)
            case scala.util.Success(_) => scala.util.Success(c)
          }
    }
}
