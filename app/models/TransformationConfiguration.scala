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

import scala.collection.immutable.Seq

/**
  * All information needed to execute a job on a Tensei-Agent.
  *
  * @param id An option to the unique database ID of the transformation configuration.
  * @param name An option to the name of the configuration.
  * @param sourceConnections A list of dfasdl connection mappings that connect the dfasdls used with the proper connection informations.
  * @param targetConnection A mapping of the target dfasdl to the proper connection information.
  * @param cookbook The cookbook that shall be used.
  * @param dirty Indicates that the related cookbook has changed it's DFASDL resources and the connection informations must be remapped!
  * @param ownerId The owner of the resource.
  * @param groupId The group that owns the resource.
  * @param groupPermissions The permissions for the group.
  * @param worldPermissions The permissions for the world e.g. any user that is not the owner or a member of the resource group.
  */
final case class TransformationConfiguration(
    id: Option[Long],
    name: Option[String],
    sourceConnections: Seq[DfasdlConnectionMapping],
    targetConnection: DfasdlConnectionMapping,
    cookbook: CookbookResource,
    dirty: Boolean,
    ownerId: Int,
    groupId: Option[Int],
    groupPermissions: Set[Permission],
    worldPermissions: Set[Permission]
) extends AuthorisableResource

object TransformationConfiguration {

  /**
    * An argonaut codec for decoding and encoding a transformation configuration from
    * and to json.
    *
    * @return The argonaut codec for json de- and encoding.
    */
  implicit def TransformationConfigurationCodec: CodecJson[TransformationConfiguration] =
    CodecJson(
      (t: TransformationConfiguration) =>
        ("id" := t.id) ->: ("name" := t.name)
        ->: ("sources" := t.sourceConnections.toList) ->: ("target" := t.targetConnection)
        ->: ("cookbook" := t.cookbook) ->: ("ownerId" := t.ownerId) ->: ("groupId" := t.groupId)
        ->: ("groupPermissions" := t.groupPermissions) ->: ("worldPermissions" := t.worldPermissions)
        ->: jEmptyObject,
      c =>
        for {
          id         <- (c --\ "id").as[Option[Long]]
          name       <- (c --\ "name").as[Option[String]]
          src        <- (c --\ "sources").as[List[DfasdlConnectionMapping]]
          dst        <- (c --\ "target").as[DfasdlConnectionMapping]
          cbk        <- (c --\ "cookbook").as[CookbookResource]
          dirty      <- (c --\ "dirty").as[Boolean]
          owner      <- (c --\ "ownerId").as[Int]
          group      <- (c --\ "groupId").as[Option[Int]]
          groupPerms <- (c --\ "groupPermissions").as[Set[Permission]]
          worldPerms <- (c --\ "worldPermissions").as[Set[Permission]]
        } yield
          TransformationConfiguration(
            id = id,
            name = name,
            sourceConnections = src,
            targetConnection = dst,
            cookbook = cbk,
            dirty = dirty,
            ownerId = owner,
            groupId = group,
            groupPermissions = groupPerms,
            worldPermissions = worldPerms
        )
    )

}
