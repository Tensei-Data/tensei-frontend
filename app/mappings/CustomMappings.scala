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

package mappings

import java.net.{ URI, URISyntaxException }

import play.api.Logger
import play.api.data.format.Formatter
import play.api.data.{ FormError, Forms, Mapping }

/**
  * This object holds several custom mappings for form elements.
  */
@SuppressWarnings(Array("org.wartremover.warts.Product", "org.wartremover.warts.Serializable"))
object CustomMappings {

  implicit val uriConverter = new Formatter[URI] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], URI] = {
      if (data.isEmpty)
        Logger.error(s"No data given in form mapping for field '$key'!")

      data
        .get(key)
        .map { value =>
          try {
            val uri = new URI(value)
            require(uri.getScheme != null, "Scheme must be defined for a Connection Information!")
            Right(uri)
          } catch {
            case e: IllegalArgumentException =>
              Logger.error("A data binding error occurred.", e)
              Left(Seq(FormError(key, e.getMessage)))
            case e: URISyntaxException =>
              Logger.error("An invalid URI specified.", e)
              Left(Seq(FormError(key, e.getMessage)))
          }
        }
        .getOrElse(Left(Seq(FormError(key, "An unexpected error occurred!"))))
    }

    override def unbind(key: String, value: URI): Map[String, String] =
      Map(key -> value.toString)
  }

  def uriType: Mapping[URI] = Forms.of[URI]

}
