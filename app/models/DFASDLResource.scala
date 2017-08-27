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

import _root_.com.wegtam.tensei.adt.DFASDL

/**
  * A wrapper for a DFASDL definition.
  *
  * @param id         An option on the database ID.
  * @param dfasdl     The actual DFASDL.
  * @param ownerId The owner of the resource.
  * @param groupId The group that owns the resource.
  * @param groupPermissions The permissions for the group.
  * @param worldPermissions The permissions for the world e.g. any user that is not the owner or a member of the resource group.
  */
final case class DFASDLResource(
    id: Option[Long],
    dfasdl: DFASDL,
    ownerId: Int,
    groupId: Option[Int],
    groupPermissions: Set[Permission],
    worldPermissions: Set[Permission]
) extends AuthorisableResource

object DFASDLResource {

  /**
    * Argonaut JSON codec for decoding and encoding a dfasdl resource from and to JSON.
    *
    * @return The argonaut codec for json de- and encoding.
    */
  implicit def DfasdlResourceCodec: CodecJson[DFASDLResource] =
    CodecJson(
      (r: DFASDLResource) =>
        ("id" := r.id) ->: ("dfasdl" := r.dfasdl)
        ->: ("ownerId" := r.ownerId) ->: ("groupId" := r.groupId) ->: ("groupPermissions" := r.groupPermissions)
        ->: ("worldPermissions" := r.worldPermissions) ->: jEmptyObject,
      c =>
        for {
          id         <- (c --\ "id").as[Option[Long]]
          dfasdl     <- (c --\ "dfasdl").as[DFASDL]
          ownerId    <- (c --\ "ownerId").as[Int]
          groupId    <- (c --\ "groupId").as[Option[Int]]
          groupPerms <- (c --\ "groupPermissions").as[Set[Permission]]
          worldPerms <- (c --\ "worldPermissions").as[Set[Permission]]
        } yield
          DFASDLResource(
            id = id,
            dfasdl = dfasdl,
            ownerId = ownerId,
            groupId = groupId,
            groupPermissions = groupPerms,
            worldPermissions = worldPerms
        )
    )

}
