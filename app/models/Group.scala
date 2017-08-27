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

/**
  * A user group.
  *
  * @param id A unique ID for the user group.
  * @param name The name of the user group that must be unique too.
  */
@SuppressWarnings(Array("org.wartremover.warts.Null"))
final case class Group(
    id: Option[Int],
    name: String
) {
  require(name != null, "The email must not be null!")
  require(name.length <= 128, "The length of an email must not be larger than 128 characters!")
}

object Group {

  /**
    * The argonaut json codec for decoding and encoding groups from and to json.
    *
    * @return The argonaut codec for json de- and encoding.
    */
  implicit def GroupCodec: CodecJson[Group] =
    CodecJson(
      (g: Group) => ("id" := g.id) ->: ("name" := g.name) ->: jEmptyObject,
      c =>
        for {
          id   <- (c --\ "id").as[Option[Int]]
          name <- (c --\ "name").as[String]
        } yield Group(id = id, name = name)
    )

}
