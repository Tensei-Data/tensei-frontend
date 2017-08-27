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

import java.sql.Timestamp
import javax.inject.Inject

import argonaut._
import Argonaut._
import com.wegtam.tensei.adt.{ Cookbook, DFASDL }
import models._
import play.api.{ Configuration, Logger }
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }
import slick.driver.JdbcProfile
import slick.lifted.{ MappedProjection, ProvenShape }

import scala.concurrent.duration._
import scala.language.higherKinds
import scalaz._

/**
  * This is a base class for all DAOs (data access objects). It provides the
  * table definitions for database tables. Single tables might be implemented
  * in seperate DAOs but if the tables are connected via relations then it is
  * more feasible to define them here.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class Tables @Inject()(protected val configuration: Configuration,
                       protected val dbConfigProvider: DatabaseConfigProvider)
    extends HasDatabaseConfigProvider[JdbcProfile] {
  import driver.api._

  val log = Logger.logger

  val DEFAULT_TIMEOUT = 10000L // The fallback default timeout for database operations in milliseconds.

  // Generic database timeout.
  lazy val dbTimeout = FiniteDuration(
    configuration.getMilliseconds("tensei.frontend.db-timeout").getOrElse(DEFAULT_TIMEOUT),
    MILLISECONDS
  )

  // The maximum duration we'll wait before we timeout a "find by id" action.
  lazy val findByIdMaxDuration = Duration(10, SECONDS)

  class GroupsTable(tag: Tag) extends Table[Group](tag, "groups") {
    def id   = column[Int]("id", O.AutoInc, O.PrimaryKey)
    def name = column[String]("name")

    def unique_name = index("groups_unique_name", name, unique = true)

    override def * : ProvenShape[Group] =
      (id.?, name).shaped <> ((Group.apply _).tupled, (g: Group) => Group.unapply(g))
  }
  lazy val groups = TableQuery[GroupsTable]

  class AccountsTable(tag: Tag) extends Table[Account](tag, "accounts") {
    def id                  = column[Int]("id", O.AutoInc, O.PrimaryKey)
    def email               = column[String]("email", O.Length(128, varying = true))
    def password            = column[String]("password", O.Length(60, varying = true))
    def isAdmin             = column[Boolean]("is_admin", O.Default(false))
    def watchedIntro        = column[Boolean]("watched_intro", O.Default(false))
    def failedLoginAttempts = column[Int]("failed_login_attempts", O.Default(0))
    def lockedAt            = column[Option[java.sql.Timestamp]]("locked_at", O.Default(None))
    def unlockToken =
      column[Option[String]]("unlock_token", O.Length(128, varying = true), O.Default(None))

    def unique_email       = index("accounts_unique_email", email, unique = true)
    def unique_unlockToken = index("accounts_unique_unlock_token", unlockToken, unique = true)

    /**
      * The shape function is somewhat massive because we set the gids to an empty set and
      * the password to an empty string upon loading from the database. Furthermore we skip
      * the gids attribute when constructing the database row.
      *
      * @return
      */
    override def * : ProvenShape[Account] =
      (id.?, email, password, isAdmin, watchedIntro, failedLoginAttempts, lockedAt, unlockToken).shaped <> (
        row =>
          Account.apply(
            id = row._1,
            email = row._2,
            password = "", // We don't load the password hash per default from the database.
            groupIds = Set.empty, // The group ids have to be loaded seperately.
            isAdmin = row._4,
            watchedIntro = row._5,
            failedLoginAttempts = row._6,
            lockedAt = row._7,
            unlockToken = row._8
        ),
        (a: Account) =>
          Account
            .unapply(a)
            .map(row => (row._1, row._2, row._3, row._5, row._6, row._7, row._8, row._9))
      )
  }
  lazy val accounts = TableQuery[AccountsTable]

  class AccountsGroupsTable(tag: Tag) extends Table[(Int, Int)](tag, "accounts_groups") {
    def accountId = column[Int]("account_id")
    def groupId   = column[Int]("group_id")

    def pk = primaryKey("accounts_groups_pk", (accountId, groupId))
    def accountIdFk =
      foreignKey("accounts_groups_fk_account_id", accountId, accounts)(
        _.id,
        onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade
      )
    def groupIdFk =
      foreignKey("accounts_groups_fk_group_id", groupId, groups)(
        _.id,
        onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade
      )

    override def * : ProvenShape[(Int, Int)] = (accountId, groupId)
  }
  lazy val accountsGroups = TableQuery[AccountsGroupsTable]

  /**
    * Mapping of DFASDLs
    */
  class DfasdlsTable(tag: Tag) extends Table[DFASDL](tag, "dfasdls") {
    def id      = column[String]("id")
    def content = column[String]("content")
    def version = column[String]("version")

    def uniqueNameAndVersionIdx = index("dfasdls_unique", (id, version), unique = true)

    def * = (id, content, version).shaped <> ((DFASDL.apply _).tupled, DFASDL.unapply)
  }
  val dfasdls = TableQuery[DfasdlsTable]

  /*
   * Helper to implicitely convert a set of permissions into an integer value
   * and vice versa.
   */
  implicit val permissionSetMapper = MappedColumnType.base[Set[Permission], Int](
    permissionSet => Permission.encodeSet(permissionSet),
    encodedPermissionSet => Permission.decode(encodedPermissionSet)
  )

  /*
   * Helper to implicitely convert a cookbook into a json string and vice versa.
   */
  implicit val cookbookMapper = MappedColumnType.base[Cookbook, String](
    cookbook => cookbook.asJson.nospaces,
    jsonString =>
      Parse.decodeEither[Cookbook](jsonString) match {
        case -\/(failure) =>
          Cookbook(id = "UNDEFINED", sources = List.empty, target = None, recipes = List.empty)
        case \/-(cookbook) => cookbook
    }
  )

  /**
    * Mapping von DFASDL resources.
    */
  type DFASDLResourceType =
    (Option[Long], String, String, Int, Option[Int], Set[Permission], Set[Permission])
  class DfasdlResourcesTable(tag: Tag) extends Table[DFASDLResourceType](tag, "dfasdlresources") {
    def id             = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def dfasdl_id      = column[String]("dfasdl_id")
    def dfasdl_version = column[String]("dfasdl_version")
    def ownerId        = column[Int]("owner_id")
    def groupId        = column[Option[Int]]("group_id")
    def groupPermissions =
      column[Set[Permission]]("group_permissions", O.Default(Permission.DEFAULT_GROUP_PERMISSIONS))
    def worldPermissions =
      column[Set[Permission]]("world_permissions", O.Default(Permission.DEFAULT_WORLD_PERMISSIONS))

    def dfasdlFk =
      foreignKey("fk_dfasdls", (dfasdl_id, dfasdl_version), dfasdls)(
        p => (p.id, p.version),
        onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade
      )
    def ownerFk =
      foreignKey("fk_dfasdls_owner_id", ownerId, accounts)(_.id,
                                                           onDelete = ForeignKeyAction.Restrict,
                                                           onUpdate = ForeignKeyAction.Cascade)
    def groupFk =
      foreignKey("fk_dfasdls_group_id", groupId, groups)(_.id.?,
                                                         onDelete = ForeignKeyAction.SetNull,
                                                         onUpdate = ForeignKeyAction.Cascade)

    override def * =
      (id.?, dfasdl_id, dfasdl_version, ownerId, groupId, groupPermissions, worldPermissions)
  }
  val dfasdlresources = TableQuery[DfasdlResourcesTable]

  /**
    * Helpers for querying related objects of dfasdl resources.
    */
  implicit class DfasdlResourceWithDfasdl[C[_]](
      q: Query[DfasdlResourcesTable, DFASDLResourceType, C]
  ) {

    /**
      * Provides a helper `.withDfasdl` that can be appended to a dfasdl resource query.
      *
      * @see http://slick.typesafe.com/doc/2.1.0/orm-to-slick.html#relationships
      */
    def withDfasdl =
      q.join(dfasdls)
        .on(
          (resource, dfasdl) =>
            resource.dfasdl_id === dfasdl.id && resource.dfasdl_version === dfasdl.version
        )
  }

  /**
    * Mapping for cookbooks (`Cookbook`)
    */
  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  class CookbooksTable(tag: Tag) extends Table[Cookbook](tag, "cookbooks") {
    def id       = column[String]("id", O.PrimaryKey)
    def cookbook = column[String]("cookbook")

    private val fromTuple: ((String, String)) => (Cookbook) = {
      case (_, c) => Parse.decodeOption[Cookbook](c).get
    }
    private val toTuple = (c: Cookbook) => Option((c.id, c.asJson.nospaces))

    def * =
      ProvenShape.proveShapeOf((id, cookbook) <> (fromTuple, toTuple))(
        MappedProjection.mappedProjectionShape
      )
  }
  val cookbooks = TableQuery[CookbooksTable]

  /**
    * Helpers for querying related objects of cookbook resources.
    */
  implicit class CookbookResourceWithCookbook[C[_]](
      q: Query[CookbookResourcesTable, CookbookResourceType, C]
  ) {

    /**
      * Provides a helper `.withCookbook` that can be appended to a cookbook resource query.
      *
      * @see http://slick.typesafe.com/doc/2.1.0/orm-to-slick.html#relationships
      */
    def withCookbook = q.join(cookbooks).on(_.cookbook_id === _.id)
  }

  /**
    * Mapping von Cookbook resources.
    */
  type CookbookResourceType =
    (Option[Long], String, Int, Option[Int], Set[Permission], Set[Permission])
  class CookbookResourcesTable(tag: Tag)
      extends Table[CookbookResourceType](tag, "cookbookresources") {
    def id          = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def cookbook_id = column[String]("cookbook_id")
    def ownerId     = column[Int]("owner_id")
    def groupId     = column[Option[Int]]("group_id")
    def groupPermissions =
      column[Set[Permission]]("group_permissions", O.Default(Permission.DEFAULT_GROUP_PERMISSIONS))
    def worldPermissions =
      column[Set[Permission]]("world_permissions", O.Default(Permission.DEFAULT_WORLD_PERMISSIONS))

    def cookbookFk =
      foreignKey("fk_cookbooks", cookbook_id, cookbooks)(_.id,
                                                         onDelete = ForeignKeyAction.Cascade,
                                                         onUpdate = ForeignKeyAction.Cascade)
    def ownerFk =
      foreignKey("fk_cookbooks_owner_id", ownerId, accounts)(_.id,
                                                             onDelete = ForeignKeyAction.Restrict,
                                                             onUpdate = ForeignKeyAction.Cascade)
    def groupFk =
      foreignKey("fk_cookbooks_group_id", groupId, groups)(_.id.?,
                                                           onDelete = ForeignKeyAction.SetNull,
                                                           onUpdate = ForeignKeyAction.Cascade)

    def * = (id.?, cookbook_id, ownerId, groupId, groupPermissions, worldPermissions)
  }
  val cookbookresources = TableQuery[CookbookResourcesTable]

  class ConnectionInformationResourcesTable(tag: Tag)
      extends Table[ConnectionInformationResource](tag, "connectioninformations") {
    def id           = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def uri          = column[String]("uri")
    def cookbook_id  = column[Option[String]]("cookbook_id")
    def dfasdl_id    = column[Option[String]]("dfasdl_id")
    def username     = column[Option[String]]("username")
    def password     = column[Option[String]]("password")
    def checksum     = column[Option[String]]("checksum")
    def language_tag = column[Option[String]]("language_tag")
    def ownerId      = column[Int]("owner_id")
    def groupId      = column[Option[Int]]("group_id")
    def groupPermissions =
      column[Set[Permission]]("group_permissions", O.Default(Permission.DEFAULT_GROUP_PERMISSIONS))
    def worldPermissions =
      column[Set[Permission]]("world_permissions", O.Default(Permission.DEFAULT_WORLD_PERMISSIONS))

    def cookbookFk =
      foreignKey("fk_cookbooks", cookbook_id, cookbooks)(_.id.?,
                                                         onDelete = ForeignKeyAction.Cascade,
                                                         onUpdate = ForeignKeyAction.Cascade)
    def ownerFk =
      foreignKey("fk_cookbooks_owner_id", ownerId, accounts)(_.id,
                                                             onDelete = ForeignKeyAction.Restrict,
                                                             onUpdate = ForeignKeyAction.Cascade)
    def groupFk =
      foreignKey("fk_cookbooks_group_id", groupId, groups)(_.id.?,
                                                           onDelete = ForeignKeyAction.SetNull,
                                                           onUpdate = ForeignKeyAction.Cascade)

    def * =
      (id.?,
       uri,
       cookbook_id,
       dfasdl_id,
       username,
       password,
       checksum,
       language_tag,
       ownerId,
       groupId,
       groupPermissions,
       worldPermissions).shaped <> (ConnectionInformationResource.fromDatabaseRow, (c: ConnectionInformationResource) =>
        Option(ConnectionInformationResource.toDatabaseRow(c)))
  }
  val connectioninformationresources = TableQuery[ConnectionInformationResourcesTable]

  type TransformationConfigurationType = (Option[Long],
                                          Option[String],
                                          Long,
                                          Long,
                                          Long,
                                          Boolean,
                                          Int,
                                          Option[Int],
                                          Set[Permission],
                                          Set[Permission])
  class TransformationConfigurationsTable(tag: Tag)
      extends Table[TransformationConfigurationType](tag, "transformationconfigurations") {
    def id                 = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name               = column[String]("name", O.Length(254, varying = true))
    def cookbookResourceId = column[Long]("cookbookresource_id")
    def targetConnectionInformationResourceId =
      column[Long]("target_connectioninformationresource_id")
    def targetDfasdlResourceId = column[Long]("target_dfasdlresource_id")
    def dirty                  = column[Boolean]("dirty", O.Default(false))
    def ownerId                = column[Int]("owner_id")
    def groupId                = column[Option[Int]]("group_id")
    def groupPermissions =
      column[Set[Permission]]("group_permissions", O.Default(Permission.DEFAULT_GROUP_PERMISSIONS))
    def worldPermissions =
      column[Set[Permission]]("world_permissions", O.Default(Permission.DEFAULT_WORLD_PERMISSIONS))

    def cookbook_Fk =
      foreignKey("cookbook_fk", cookbookResourceId, cookbookresources)(
        _.id,
        onDelete = ForeignKeyAction.Restrict,
        onUpdate = ForeignKeyAction.Cascade
      )
    def ownerFk =
      foreignKey("fk_transformationconfigurations_owner_id", ownerId, accounts)(
        _.id,
        onDelete = ForeignKeyAction.Restrict,
        onUpdate = ForeignKeyAction.Cascade
      )
    def groupFk =
      foreignKey("fk_transformationconfigurations_group_id", groupId, groups)(
        _.id.?,
        onDelete = ForeignKeyAction.SetNull,
        onUpdate = ForeignKeyAction.Cascade
      )

    def * =
      (id.?,
       name.?,
       targetConnectionInformationResourceId,
       targetDfasdlResourceId,
       cookbookResourceId,
       dirty,
       ownerId,
       groupId,
       groupPermissions,
       worldPermissions)
  }
  val transformationconfigurations = TableQuery[TransformationConfigurationsTable]

  class TransformationConfigurationSources(tag: Tag)
      extends Table[(Long, Long, Long)](tag, "transformationconfigurationconnectionsources") {
    def transformationConfigurationId = column[Long]("transformationconfiguration_id")
    def connectionResourceId          = column[Long]("connectioninformationresource_id")
    def dfasdlResourceId              = column[Long]("dfasdlresource_id")

    def pk =
      primaryKey("pk_transformationconfigurationsources",
                 (transformationConfigurationId, dfasdlResourceId))
    def tcFk =
      foreignKey("tc_fk", transformationConfigurationId, transformationconfigurations)(
        _.id,
        onDelete = ForeignKeyAction.Restrict,
        onUpdate = ForeignKeyAction.Cascade
      )
    def crFk =
      foreignKey("cr_fk", connectionResourceId, connectioninformationresources)(
        _.id,
        onDelete = ForeignKeyAction.Restrict,
        onUpdate = ForeignKeyAction.Cascade
      )
    def drFk =
      foreignKey("dr_fk", dfasdlResourceId, dfasdlresources)(_.id,
                                                             onDelete = ForeignKeyAction.Restrict,
                                                             onUpdate = ForeignKeyAction.Cascade)

    def * = (transformationConfigurationId, connectionResourceId, dfasdlResourceId)
  }
  val transformationconfigurationsources = TableQuery[TransformationConfigurationSources]

  class CronTable(tag: Tag) extends Table[Cron](tag, "crontab") {
    def id          = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def tkid        = column[Long]("tkid")
    def description = column[Option[String]]("description")
    def format      = column[String]("format")
    def active      = column[Boolean]("active")
    def ownerId     = column[Int]("owner_id")
    def groupId     = column[Option[Int]]("group_id")
    def groupPermissions =
      column[Set[Permission]]("group_permissions", O.Default(Permission.DEFAULT_GROUP_PERMISSIONS))
    def worldPermissions =
      column[Set[Permission]]("world_permissions", O.Default(Permission.DEFAULT_WORLD_PERMISSIONS))

    def tcFk =
      foreignKey("fk_crontab_tk_id", tkid, transformationconfigurations)(
        _.id,
        onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade
      )
    def ownerFk =
      foreignKey("fk_crontab_owner_id", ownerId, accounts)(_.id,
                                                           onDelete = ForeignKeyAction.Restrict,
                                                           onUpdate = ForeignKeyAction.Cascade)
    def groupFk =
      foreignKey("fk_crontab_group_id", groupId, groups)(_.id.?,
                                                         onDelete = ForeignKeyAction.SetNull,
                                                         onUpdate = ForeignKeyAction.Cascade)

    def * =
      (id.?,
       tkid,
       description,
       format,
       active,
       ownerId,
       groupId,
       groupPermissions,
       worldPermissions).shaped <> ((Cron.apply _).tupled, (c: Cron) => Cron.unapply(c))
  }
  val crontab = TableQuery[CronTable]

  class TriggerTable(tag: Tag) extends Table[Trigger](tag, "triggers") {
    def id          = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def tkid        = column[Long]("tkid")
    def description = column[Option[String]]("description")
    def endpointuri = column[Option[String]]("endpoint_uri")
    def triggerTkId = column[Option[Long]]("trigger_tkid")
    def active      = column[Boolean]("active")
    def ownerId     = column[Int]("owner_id")
    def groupId     = column[Option[Int]]("group_id")
    def groupPermissions =
      column[Set[Permission]]("group_permissions", O.Default(Permission.DEFAULT_GROUP_PERMISSIONS))
    def worldPermissions =
      column[Set[Permission]]("world_permissions", O.Default(Permission.DEFAULT_WORLD_PERMISSIONS))

    def tcFk =
      foreignKey("fk_triggers_tk_id", tkid, transformationconfigurations)(
        _.id,
        onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade
      )
    def ownerFk =
      foreignKey("fk_triggers_owner_id", ownerId, accounts)(_.id,
                                                            onDelete = ForeignKeyAction.Restrict,
                                                            onUpdate = ForeignKeyAction.Cascade)
    def groupFk =
      foreignKey("fk_triggers_group_id", groupId, groups)(_.id.?,
                                                          onDelete = ForeignKeyAction.SetNull,
                                                          onUpdate = ForeignKeyAction.Cascade)

    override def * =
      (id.?,
       tkid,
       description,
       endpointuri,
       triggerTkId,
       active,
       ownerId,
       groupId,
       groupPermissions,
       worldPermissions).shaped <> ((Trigger.apply _).tupled, (t: Trigger) => Trigger.unapply(t))
  }
  val triggers = TableQuery[TriggerTable]

  class WorkingQueueTable(tag: Tag) extends Table[WorkQueueEntry](tag, "tcqueue") {
    def uuid      = column[String]("uuid", O.PrimaryKey)
    def tkid      = column[Long]("tkid")
    def sortorder = column[Int]("sortorder")
    def created   = column[Timestamp]("created")
    def cron      = column[Boolean]("cron")
    def trigger   = column[Boolean]("trigger")
    def user      = column[Option[Int]]("user")

    def tcFk =
      foreignKey("fk_tcqueue_tkid", tkid, transformationconfigurations)(
        _.id,
        onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade
      )
    def userFk =
      foreignKey("fk_tcqueue_user", user, accounts)(_.id.?,
                                                    onDelete = ForeignKeyAction.Restrict,
                                                    onUpdate = ForeignKeyAction.Cascade)

    def * =
      (uuid, tkid, sortorder, created, cron, trigger, user).shaped <> ((WorkQueueEntry.apply _).tupled, (e: WorkQueueEntry) =>
        WorkQueueEntry.unapply(e))
  }
  val workQueue = TableQuery[WorkingQueueTable]

  /**
    * Helpers for querying related objects of dfasdl resources.
    */
  implicit class QueueEntryWithTransformationConfig[C[_]](
      q: Query[WorkingQueueTable, WorkQueueEntry, C]
  ) {

    /**
      * Provides a helper `.withTransformationConfig` that can be appended to a transformation config queue query.
      *
      * @see http://slick.typesafe.com/doc/2.1.0/orm-to-slick.html#relationships
      */
    def withTransformationConfig =
      q.join(transformationconfigurations).on((resource, config) => resource.tkid === config.id)
  }

  class WorkHistoryTable(tag: Tag) extends Table[WorkHistoryEntry](tag, "tcqueuehistory") {
    def uuid      = column[String]("uuid", O.PrimaryKey)
    def tkid      = column[Long]("tkid")
    def created   = column[Timestamp]("created")
    def started   = column[Option[Timestamp]]("started")
    def finished  = column[Option[Timestamp]]("finished")
    def cron      = column[Boolean]("cron")
    def trigger   = column[Boolean]("trigger")
    def user      = column[Option[Int]]("user")
    def completed = column[Boolean]("completed")
    def aborted   = column[Boolean]("aborted")
    def error     = column[Boolean]("error")
    def message   = column[Option[String]]("message")

    def tcFk =
      foreignKey("fk_tcqueue_tkid", tkid, transformationconfigurations)(
        _.id,
        onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade
      )
    def userFk =
      foreignKey("fk_tcqueue_user", user, accounts)(_.id.?,
                                                    onDelete = ForeignKeyAction.Restrict,
                                                    onUpdate = ForeignKeyAction.Cascade)

    def * =
      (uuid,
       tkid,
       created,
       started,
       finished,
       cron,
       trigger,
       user,
       completed,
       aborted,
       error,
       message).shaped <> ((WorkHistoryEntry.apply _).tupled, (e: WorkHistoryEntry) =>
        WorkHistoryEntry.unapply(e))
  }
  val workHistory = TableQuery[WorkHistoryTable]

  /**
    * Helpers for querying related objects of dfasdl resources.
    */
  implicit class HistoryEntryWithTransformationConfig[C[_]](
      q: Query[WorkHistoryTable, WorkHistoryEntry, C]
  ) {

    /**
      * Provides a helper `.withTransformationConfig` that can be appended to a transformation history queue query.
      *
      * @see http://slick.typesafe.com/doc/2.1.0/orm-to-slick.html#relationships
      */
    def withTransformationConfig =
      q.join(transformationconfigurations).on((resource, config) => resource.tkid === config.id)
  }

}
