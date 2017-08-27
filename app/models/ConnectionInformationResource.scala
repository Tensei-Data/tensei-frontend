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

import java.net.URI

import com.wegtam.tensei.adt.{ ConnectionInformation, DFASDLReference }

/**
  * A wrapper for a connection information.
  *
  * @param id         The database ID of the connection information.
  * @param connection The actual connection information.
  * @param ownerId The owner of the resource.
  * @param groupId The group that owns the resource.
  * @param groupPermissions The permissions for the group.
  * @param worldPermissions The permissions for the world e.g. any user that is not the owner or a member of the resource group.
  */
final case class ConnectionInformationResource(
    id: Option[Long] = None,
    connection: ConnectionInformation,
    ownerId: Int,
    groupId: Option[Int],
    groupPermissions: Set[Permission],
    worldPermissions: Set[Permission]
) extends AuthorisableResource

object ConnectionInformationResource {

  def fromDatabaseRow(
      row: (Option[Long],
            String,
            Option[String],
            Option[String],
            Option[String],
            Option[String],
            Option[String],
            Option[String],
            Int,
            Option[Int],
            Set[Permission],
            Set[Permission])
  ): ConnectionInformationResource = {
    val ref: Option[DFASDLReference] = row._3.fold(None: Option[DFASDLReference])(
      cid =>
        row._4.fold(None: Option[DFASDLReference])(
          did => Option(DFASDLReference(cookbookId = cid, dfasdlId = did))
      )
    )
    ConnectionInformationResource(
      id = row._1,
      connection = ConnectionInformation(
        uri = new URI(row._2),
        dfasdlRef = ref,
        username = row._5,
        password = row._6,
        checksum = row._7,
        languageTag = row._8
      ),
      ownerId = row._9,
      groupId = row._10,
      groupPermissions = row._11,
      worldPermissions = row._12
    )
  }

  def toDatabaseRow(c: ConnectionInformationResource): (Option[Long],
                                                        String,
                                                        Option[String],
                                                        Option[String],
                                                        Option[String],
                                                        Option[String],
                                                        Option[String],
                                                        Option[String],
                                                        Int,
                                                        Option[Int],
                                                        Set[Permission],
                                                        Set[Permission]) = {
    val cid = c.connection.dfasdlRef.fold(None: Option[String])(ref => Option(ref.cookbookId))
    val did = c.connection.dfasdlRef.fold(None: Option[String])(ref => Option(ref.dfasdlId))
    (c.id,
     c.connection.uri.toString,
     cid,
     did,
     c.connection.username,
     c.connection.password,
     c.connection.checksum,
     c.connection.languageTag,
     c.ownerId,
     c.groupId,
     c.groupPermissions,
     c.worldPermissions)
  }

  /**
    * The argonaut codec for decoding and encoding a connection information resource from
    * and to json.
    *
    * @return The argonaut codec for de- and encoding json.
    */
  implicit def ConnectionInformationResourceCodec: CodecJson[ConnectionInformationResource] =
    CodecJson(
      (c: ConnectionInformationResource) =>
        ("id" := c.id) ->: ("connection" := c.connection)
        ->: ("ownerId" := c.ownerId) ->: ("groupId" := c.groupId)
        ->: ("groupPermissions" := c.groupPermissions) ->: ("worldPermissions" := c.worldPermissions)
        ->: jEmptyObject,
      c =>
        for {
          id         <- (c --\ "id").as[Option[Long]]
          con        <- (c --\ "connection").as[ConnectionInformation]
          owner      <- (c --\ "ownerId").as[Int]
          group      <- (c --\ "groupId").as[Option[Int]]
          groupPerms <- (c --\ "groupPermissions").as[Set[Permission]]
          worldPerms <- (c --\ "worldPermissions").as[Set[Permission]]
        } yield
          ConnectionInformationResource(
            id = id,
            connection = con,
            ownerId = owner,
            groupId = group,
            groupPermissions = groupPerms,
            worldPermissions = worldPerms
        )
    )

}
