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

import com.wegtam.tensei.adt.DFASDL
import controllers.DFASDLResourcesController
import models.{ DFASDLResource, Permission }
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/**
  * A data access object (DAO) for handling dfasdl resources and their connected DFASDLs.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class DFASDLResourceDAO @Inject()(override protected val configuration: Configuration,
                                  override protected val dbConfigProvider: DatabaseConfigProvider)
    extends Tables(configuration, dbConfigProvider) {
  import driver.api._

  /**
    * Return all dfasdl resources that are readable by the user.
    *
    * @param ownerId The ID of the user.
    * @param groupIds A list of groups ids the user is a member of.
    * @param loadDfasdls Indicates if the connected dfasdls should be loaded or not.
    * @param context An implicit execution context.
    * @return A future holding a list of dfasdl resources.
    */
  def allReadable(ownerId: Int, groupIds: Set[Int])(
      loadDfasdls: Boolean
  )(implicit context: ExecutionContext): Future[Seq[DFASDLResource]] = {
    val baseQuery = dfasdlresources.filter(
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
    if (loadDfasdls)
      dbConfig.db
        .run(query.withDfasdl.sortBy(_._1.dfasdl_id.asc).result)
        .map(
          rows =>
            rows.map { row =>
              val res    = row._1
              val dfasdl = row._2
              DFASDLResource(
                id = res._1,
                dfasdl = dfasdl,
                ownerId = res._4,
                groupId = res._5,
                groupPermissions = res._6,
                worldPermissions = res._7
              )
          }
        )
    else
      dbConfig.db
        .run(query.result)
        .map(
          rows =>
            rows.map { row =>
              DFASDLResource(
                id = row._1,
                dfasdl = DFASDL(id = row._2,
                                content = DFASDLResourcesController.DfasdlContentPlaceholder,
                                version = row._3),
                ownerId = row._4,
                groupId = row._5,
                groupPermissions = row._6,
                worldPermissions = row._7
              )
          }
        )
  }

  /**
    * Create the given dfasdl resource in the database. The included DFASDL will
    * be created in the dfasdl table.
    *
    * @param d A DFASDL resource.
    * @param context An implicit execution context.
    * @return A future holding the created resource.
    */
  def create(d: DFASDLResource)(implicit context: ExecutionContext): Future[Try[DFASDLResource]] = {
    val row = (d.id,
               d.dfasdl.id,
               d.dfasdl.version,
               d.ownerId,
               d.groupId,
               d.groupPermissions,
               d.worldPermissions)
    dbConfig.db
      .run(
        (dfasdls += d.dfasdl)
          .andThen((dfasdlresources returning dfasdlresources.map(_.id)) += row)
          .asTry
      )
      .map {
        case scala.util.Failure(t) => scala.util.Failure(t)
        case scala.util.Success(r) => scala.util.Success(d.copy(id = Option(r)))
      }
  }

  /**
    * Destroy the DFASDL resource in the database.
    *
    * @param d The DFASDL resource that must be destroyed
    * @param context An implicit execution context.
    * @return A future holding the number of affected database rows.
    */
  def destroy(d: DFASDLResource)(implicit context: ExecutionContext): Future[Int] =
    for {
      deleted <- d.id.fold(Future.successful(0))(
        id => dbConfig.db.run(dfasdlresources.filter(_.id === id).delete)
      )
      deletedDfasdls <- dbConfig.db.run(dfasdls.filter(_.id === d.dfasdl.id).delete) if deleted > 0
    } yield deleted

  /**
    * Return the first dfasdl resource from that database that holds the given
    * dfasdl id.
    *
    * @param dfasdlId The ID of a DFASDL.
    * @param context An implicit execution context.
    * @return A future holding an option to the dfasdl resource.
    */
  def findByDfasdlId(
      dfasdlId: String
  )(implicit context: ExecutionContext): Future[Option[DFASDLResource]] =
    dbConfig.db
      .run(dfasdlresources.filter(_.dfasdl_id === dfasdlId).withDfasdl.result.headOption)
      .map(
        o =>
          o.map { row =>
            val res    = row._1
            val dfasdl = row._2
            DFASDLResource(
              id = res._1,
              dfasdl = dfasdl,
              ownerId = res._4,
              groupId = res._5,
              groupPermissions = res._6,
              worldPermissions = res._7
            )
        }
      )

  /**
    * Load all dfasdl resources from the database that are specified by the list of given
    * DFASDL ids.
    *
    * @param ids A list of dfasdl ids.
    * @param context An implicit execution context.
    * @return A future holding a list of dfasdl resources.
    */
  def findByDfasdlIds(
      ids: Set[String]
  )(implicit context: ExecutionContext): Future[Seq[DFASDLResource]] =
    dbConfig.db
      .run(dfasdlresources.filter(_.dfasdl_id.inSetBind(ids)).withDfasdl.result)
      .map(
        rs =>
          rs.map { row =>
            val res    = row._1
            val dfasdl = row._2
            DFASDLResource(
              id = res._1,
              dfasdl = dfasdl,
              ownerId = res._4,
              groupId = res._5,
              groupPermissions = res._6,
              worldPermissions = res._7
            )
        }
      )

  /**
    * Load the dfasdl resource with the given ID from the database.
    *
    * @param id The database id of the dfasdl resource.
    * @param loadDfasdl Indicates if the connected dfasdl should be loaded or not.
    * @param context An implicit execution context.
    * @return A future holding an option to the dfasdl resource.
    */
  def findById(
      id: Long
  )(loadDfasdl: Boolean)(implicit context: ExecutionContext): Future[Option[DFASDLResource]] =
    if (loadDfasdl)
      dbConfig.db
        .run(dfasdlresources.filter(_.id === id).withDfasdl.result.headOption)
        .map(
          o =>
            o.map { row =>
              val res    = row._1
              val dfasdl = row._2
              DFASDLResource(
                id = res._1,
                dfasdl = dfasdl,
                ownerId = res._4,
                groupId = res._5,
                groupPermissions = res._6,
                worldPermissions = res._7
              )
          }
        )
    else
      dbConfig.db
        .run(dfasdlresources.filter(_.id === id).result.headOption)
        .map(
          o =>
            o.map { row =>
              DFASDLResource(
                id = row._1,
                dfasdl = DFASDL(id = row._2,
                                content = DFASDLResourcesController.DfasdlContentPlaceholder,
                                version = row._3),
                ownerId = row._4,
                groupId = row._5,
                groupPermissions = row._6,
                worldPermissions = row._7
              )
          }
        )

  /**
    * Load all dfasdl resources from the database that are specified by the list of given ids.
    *
    * @param ids A list of database ids of dfasdl resources.
    * @param loadDfasdl Indicates if the connected dfasdl should be loaded or not.
    * @param context An implicit execution context.
    * @return A future holding a list of dfasdl resources.
    */
  def findByIds(
      ids: Set[Long]
  )(loadDfasdl: Boolean)(implicit context: ExecutionContext): Future[Seq[DFASDLResource]] =
    if (loadDfasdl)
      dbConfig.db
        .run(dfasdlresources.filter(_.id.inSetBind(ids)).withDfasdl.result)
        .map(
          rs =>
            rs.map { row =>
              val res    = row._1
              val dfasdl = row._2
              DFASDLResource(
                id = res._1,
                dfasdl = dfasdl,
                ownerId = res._4,
                groupId = res._5,
                groupPermissions = res._6,
                worldPermissions = res._7
              )
          }
        )
    else
      dbConfig.db
        .run(dfasdlresources.filter(_.id.inSetBind(ids)).result)
        .map(
          rs =>
            rs.map { row =>
              DFASDLResource(
                id = row._1,
                dfasdl = DFASDL(id = row._2,
                                content = DFASDLResourcesController.DfasdlContentPlaceholder,
                                version = row._3),
                ownerId = row._4,
                groupId = row._5,
                groupPermissions = row._6,
                worldPermissions = row._7
              )
          }
        )

  /**
    * Query the database for cookbooks that are using the given DFASDL ID.
    * The number of cookbooks is returned.
    *
    * @param dfasdlId The ID of a DFASDL.
    * @return A future holding the number of cookbooks that use the DFASDL.
    */
  def isUsedInCookbooks(dfasdlId: String): Future[Int] = {
    val idString = s"""%"id":"$dfasdlId"%"""
    val query = for {
      cookbook <- cookbooks if cookbook.cookbook like idString
    } yield cookbook.id
    dbConfig.db.run(query.length.result)
  }

  /**
    * Load the DFASDL with the given ID and version.
    *
    * @param id The ID of the DFASDL.
    * @param version The version of the DFASDL.
    * @return A future holding an option to the DFASDL.
    */
  def loadDfasdlVersion(id: String, version: String): Future[Option[DFASDL]] =
    dbConfig.db.run(
      dfasdls.filter(dfasdl => dfasdl.id === id && dfasdl.version === version).result.headOption
    )

  /**
    * Load the versions of the given DFASDL id from the database.
    *
    * @param id The ID of the DFASDL.
    * @return A future holding a list of version strings.
    */
  def loadDfasdlVersions(id: String): Future[Seq[String]] =
    dbConfig.db.run(
      dfasdls.filter(_.id === id).sortBy(_.version.asc).map(_.version).distinct.result
    )

  /**
    * Update the given dfasdl resource and the attached DFASDL in the database.
    *
    * If the attached DFASDL is renamed (change of ID) then all stored DFASDLs
    * with the old ID are renamed accordingly.
    *
    * @param d A dfasdl resource.
    * @param context An implicit execution context.
    * @return A future holding the number of affected database rows.
    */
  def update(d: DFASDLResource)(implicit context: ExecutionContext): Future[Int] =
    d.id.fold(Future.successful(0)) { id =>
      // Try to create the next dfasdl version and fallback to `1` in case of an error.
      val nextDfasdlVersion: Long = Try {
        d.dfasdl.version.toLong + 1
      } match {
        case Failure(failure) =>
          log.error("Could not parse dfasdl version, using fallback version!", failure)
          log.error("DFASDL version leading to this error: {}", d.dfasdl.version)
          1L
        case Success(success) => success
      }

      for {
        currentResource <- dbConfig.db.run(dfasdlresources.filter(_.id === id).result.headOption)
        renameOldDfasdls <- currentResource.fold(Future.successful(0))(
          r =>
            // Rename the old saved dfasdl versions if the ID of the dfasdl has changed.
            if (r._2 != d.dfasdl.id)
              dbConfig.db.run(dfasdls.filter(_.id === r._2).map(_.id).update(d.dfasdl.id))
            else
              Future.successful(0)
        )
        newDfasdl: DFASDL <- dbConfig.db
          .run(dfasdls += d.dfasdl.copy(version = nextDfasdlVersion.toString))
          .map(f => d.dfasdl.copy(version = nextDfasdlVersion.toString))
        updatedResource <- dbConfig.db.run(
          dfasdlresources
            .filter(_.id === id)
            .update(
              (d.id,
               newDfasdl.id,
               newDfasdl.version,
               d.ownerId,
               d.groupId,
               d.groupPermissions,
               d.worldPermissions)
            )
        )
      } yield updatedResource
    }
}
