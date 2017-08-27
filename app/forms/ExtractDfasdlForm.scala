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

package forms

import java.nio.charset.Charset

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{ Constraint, Invalid, Valid }

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

/**
  * Form definition for parameterising the extraction of a DFASDL schema
  * from a connection information.
  */
object ExtractDfasdlForm {
  final val Charsets: Seq[String] =
    Charset.availableCharsets().asScala.keySet.toVector.sorted.map(_.toLowerCase)
  final val DEFAULT_ENCODING  = "utf-8"
  final val DEFAULT_SEPARATOR = ","

  val encodingConstraint: Constraint[String] = Constraint("constraints.encoding-check")({
    encoding =>
      if (encoding.isEmpty || Charsets.contains(encoding))
        Valid
      else
        Invalid(Seq.empty)
  })

  val form = Form(
    mapping(
      "encoding"  -> text.verifying(encodingConstraint),
      "header"    -> boolean,
      "separator" -> text
    )(Data.apply)(Data.unapply)
  )

  final case class Data(encoding: String, header: Boolean, separator: String)

}
