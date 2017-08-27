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

import com.wegtam.tensei.adt.Cookbook

/**
  * A wrapper for a cookbook.
  *
  * @param id         The database ID.
  * @param cookbook   The actual cookbook.
  * @param ownerId The owner of the resource.
  * @param groupId The group that owns the resource.
  * @param groupPermissions The permissions for the group.
  * @param worldPermissions The permissions for the world e.g. any user that is not the owner or a member of the resource group.
  */
final case class CookbookResource(
    id: Option[Long],
    cookbook: Cookbook,
    ownerId: Int,
    groupId: Option[Int],
    groupPermissions: Set[Permission],
    worldPermissions: Set[Permission]
) extends AuthorisableResource

object CookbookResource {

  /**
    * Argonaut JSON codec for decoding and encoding a cookbook resource from and to JSON.
    *
    * @return The argonaut codec for json de- and encoding.
    */
  implicit def CookbookResourceCodec: CodecJson[CookbookResource] =
    CodecJson(
      (r: CookbookResource) =>
        ("id" := r.id) ->: ("cookbook" := r.cookbook)
        ->: ("ownerId" := r.ownerId) ->: ("groupId" := r.groupId) ->: ("groupPermissions" := r.groupPermissions)
        ->: ("worldPermissions" := r.worldPermissions) ->: jEmptyObject,
      c =>
        for {
          id         <- (c --\ "id").as[Option[Long]]
          cookbook   <- (c --\ "cookbook").as[Cookbook]
          ownerId    <- (c --\ "ownerId").as[Int]
          groupId    <- (c --\ "groupId").as[Option[Int]]
          groupPerms <- (c --\ "groupPermissions").as[Set[Permission]]
          worldPerms <- (c --\ "worldPermissions").as[Set[Permission]]
        } yield
          CookbookResource(
            id = id,
            cookbook = cookbook,
            ownerId = ownerId,
            groupId = groupId,
            groupPermissions = groupPerms,
            worldPermissions = worldPerms
        )
    )

}
