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
import models.{ CookbookResource, DfasdlConnectionMapping, Permission, TransformationConfiguration }
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
  * A data access object (DAO) for handling transformation configurations.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class TransformationConfigurationDAO @Inject()(
    override protected val configuration: Configuration,
    override protected val dbConfigProvider: DatabaseConfigProvider
) extends Tables(configuration, dbConfigProvider) {
  import profile.api._

  /**
    * Helper function to construct a transformation configuration model from
    * a joined database query result.
    *
    * @todo Find a way to push the logic from here into a database query!
    *
    * @param rows The database rows from a joined query of transformation configuration table and the cookbook resources table.
    * @param sourceConnectionRows The rows from the helper table to map the source dfasdl connections regarding the current tranformation id.
    * @return An option to the constructed transformation configuration.
    */
  private def constructFromDatabaseRows(
      rows: Option[
        ((Option[Long],
          Option[String],
          Long,
          Long,
          Long,
          Boolean,
          Int,
          Option[Int],
          Set[Permission],
          Set[Permission]),
         Option[(Option[Long], String, Int, Option[Int], Set[Permission], Set[Permission])])
      ],
      sourceConnectionRows: Seq[(Long, Long, Long)]
  ): Option[TransformationConfiguration] =
    rows.fold(None: Option[TransformationConfiguration])(
      row =>
        row._2.fold(None: Option[TransformationConfiguration]) { cdata =>
          row._1._1.fold {
            log.error("Missing ID column from transformation configuration!")
            None: Option[TransformationConfiguration]
          } {
            tid =>
              val cr = CookbookResource(
                id = cdata._1,
                cookbook = Cookbook(
                  id = cdata._2,
                  sources = List.empty,
                  target = None,
                  recipes = List.empty
                ),
                ownerId = cdata._3,
                groupId = cdata._4,
                groupPermissions = cdata._5,
                worldPermissions = cdata._6
              )
              val sources = sourceConnectionRows
                .filter(_._1 == tid)
                .map(
                  sc => DfasdlConnectionMapping(dfasdlId = sc._3, connectionInformationId = sc._2)
                )
                .toList
              Option(
                TransformationConfiguration(
                  id = row._1._1,
                  name = row._1._2,
                  sourceConnections = sources,
                  targetConnection = DfasdlConnectionMapping(dfasdlId = row._1._4,
                                                             connectionInformationId = row._1._3),
                  cookbook = cr,
                  dirty = row._1._6,
                  ownerId = row._1._7,
                  groupId = row._1._8,
                  groupPermissions = row._1._9,
                  worldPermissions = row._1._10
                )
              )
          }
      }
    )

  /**
    * Return all transformation configurations from the database that are
    * readable by the given user and groups.
    *
    * '''For performance reasons the attached cookbook resource is not fully loaded!'''
    *
    * @param ownerId The id of the user.
    * @param groupIds A list of group ids.
    * @param context An implicit execution context.
    * @return A future holding the list of all readable transformation configurations.
    */
  def allReadable(ownerId: Int, groupIds: Set[Int])(
      implicit context: ExecutionContext
  ): Future[Seq[TransformationConfiguration]] = {
    val baseQuery = transformationconfigurations.filter(
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
    for {
      tcs <- dbConfig.db.run(
        query.joinLeft(cookbookresources).on(_.cookbookResourceId === _.id).result
      )
      ids <- {
        val tids: Set[Long] = tcs.flatMap(_._1._1).toSet
        dbConfig.db.run(
          transformationconfigurationsources
            .filter(_.transformationConfigurationId.inSetBind(tids))
            .result
        )
      }
    } yield tcs.flatMap(r => constructFromDatabaseRows(Option(r), ids))
  }

  /**
    * Count the number of entries in the transformation configuration table.
    *
    * @return A future holding the number of transformation configurations.
    */
  def count: Future[Int] = dbConfig.db.run(transformationconfigurations.length.result)

  /**
    * Create the given transformation configuration in the database.
    *
    * @param t A transformation configuration.
    * @param context An implicit execution context.
    * @return A future holding an option to the created transformation configuration.
    */
  def create(
      t: TransformationConfiguration
  )(implicit context: ExecutionContext): Future[Try[TransformationConfiguration]] =
    t.cookbook.id.fold(
      Future.successful(
        scala.util.Failure(new IllegalArgumentException("Cookbook has no ID!")): Try[
          TransformationConfiguration
        ]
      )
    ) { cid =>
      val row = (t.id,
                 t.name,
                 t.targetConnection.connectionInformationId,
                 t.targetConnection.dfasdlId,
                 cid,
                 t.dirty,
                 t.ownerId,
                 t.groupId,
                 t.groupPermissions,
                 t.worldPermissions)
      for {
        saveConfig <- dbConfig.db.run(
          ((transformationconfigurations returning transformationconfigurations
            .map(_.id)) += row).asTry
        )
        mappedSources <- saveConfig match {
          case scala.util.Failure(_) => Future.successful(Seq.empty)
          case scala.util.Success(id) =>
            Future.successful(
              t.sourceConnections.map(c => (id, c.connectionInformationId, c.dfasdlId))
            )
        }
        saveSources <- saveConfig match {
          case scala.util.Failure(e) => Future.successful(scala.util.Failure(e))
          case scala.util.Success(_) =>
            dbConfig.db.run((transformationconfigurationsources ++= mappedSources).asTry)
        }
      } yield
        saveConfig match {
          case scala.util.Failure(e) => scala.util.Failure(e)
          case scala.util.Success(id) =>
            saveSources match {
              case scala.util.Failure(e) => scala.util.Failure(e)
              case scala.util.Success(_) => scala.util.Success(t.copy(id = Option(id)))
            }
        }
    }

  /**
    * Destroy the given transformation configuration in the database.
    *
    * @param t A transformation configuration.
    * @return A future holding the number of affected database rows.
    */
  def destroy(t: TransformationConfiguration): Future[Int] =
    t.id.fold(Future.successful(0))(
      id => dbConfig.db.run(transformationconfigurations.filter(_.id === id).delete)
    )

  /**
    * Check if the cookbook resource with the given id is used by any transformation
    * configurations.
    *
    * @param cid The database ID of a cookbook resource.
    * @return A future holding the number of transformation configurations that use the cookbook resource.
    */
  def existsByCookbookResourceId(cid: Long): Future[Int] =
    dbConfig.db.run(
      transformationconfigurations.filter(_.cookbookResourceId === cid).length.result
    )

  /**
    * Load the transformation configuration with the given ID from the database.
    *
    * '''For performance reasons the attached cookbook resource is not fully loaded!'''
    *
    * @param id The ID of the transformation configuration.
    * @param context An implicit execution context.
    * @return A future holding an option to the transformation configuration.
    */
  def findById(
      id: Long
  )(implicit context: ExecutionContext): Future[Option[TransformationConfiguration]] =
    for {
      tc <- dbConfig.db.run(
        transformationconfigurations
          .filter(_.id === id)
          .joinLeft(cookbookresources)
          .on(_.cookbookResourceId === _.id)
          .result
          .headOption
      )
      ids <- dbConfig.db.run(
        transformationconfigurationsources.filter(_.transformationConfigurationId === id).result
      )
    } yield constructFromDatabaseRows(tc, ids)

  /**
    * Set the column `dirty` to `true` for all transformation configurations that
    * are using the cookbookresource with the given id.
    *
    * @param cid The database ID of a cookbook resource.
    * @return A future holding the number of affected database rows.
    */
  def markDirtyByCookbookResourceId(cid: Long): Future[Int] =
    dbConfig.db.run(
      transformationconfigurations.filter(_.cookbookResourceId === cid).map(_.dirty).update(true)
    )

  /**
    * Update the given transformation configuration in the database.
    *
    * @param t A transformation configuration.
    * @param context An implicit execution context.
    * @return A future holding the number of affected database rows.
    */
  def update(t: TransformationConfiguration)(implicit context: ExecutionContext): Future[Int] =
    t.id.fold(Future.successful(0))(
      id =>
        t.cookbook.id.fold(Future.successful(0)) { cid =>
          val row = (t.id,
                     t.name,
                     t.targetConnection.connectionInformationId,
                     t.targetConnection.dfasdlId,
                     cid,
                     t.dirty,
                     t.ownerId,
                     t.groupId,
                     t.groupPermissions,
                     t.worldPermissions)
          for {
            del <- dbConfig.db.run(
              transformationconfigurationsources
                .filter(_.transformationConfigurationId === id)
                .delete
            ) // Remove old sources links.
            r <- dbConfig.db.run(transformationconfigurations.filter(_.id === id).update(row))
            cs <- Future.successful(
              t.sourceConnections.map(sc => (id, sc.connectionInformationId, sc.dfasdlId))
            )
            s <- dbConfig.db.run(transformationconfigurationsources ++= cs)
          } yield r
      }
    )

}
