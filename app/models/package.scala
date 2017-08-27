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

import argonaut._, Argonaut._

package object models {

  /**
    * An argonaut json codec for converting java.sql.Timestamp instances from
    * and to json.
    *
    * @return An argonaut codec for java.sql.Timestamp instances.
    */
  implicit def javaSqlTimestampCodec: CodecJson[java.sql.Timestamp] =
    CodecJson(
      (t: java.sql.Timestamp) => jString(t.toString),
      c =>
        for {
          t <- c.as[String]
        } yield java.sql.Timestamp.valueOf(t)
    )

}
