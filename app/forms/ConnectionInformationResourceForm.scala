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

import java.net.URI
import java.util.Locale

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }

import scala.util.{ Failure, Success, Try }

/**
  * This form handles the creation and editing of a [[models.ConnectionInformationResource]].
  */
object ConnectionInformationResourceForm {

  final val AvailableLocales: Array[Locale] = java.util.Locale.getAvailableLocales
    .filterNot(_.toLanguageTag == "und")
    .sortBy(_.getDisplayLanguage)

  /*
   * A custom constraint that verifies a given string for being a valid URI.
   */
  val uriCheckConstraint: Constraint[String] = Constraint("constraints.uri-check")({ uriString =>
    val errors = Try {
      new URI(uriString)
    } match {
      case Failure(f) => Seq(ValidationError(f.getMessage))
      case Success(_) => Nil
    }
    if (errors.isEmpty)
      Valid
    else
      Invalid(errors)
  })

  // A play framework form definition.
  val form = Form(
    mapping(
      "uri" -> nonEmptyText
        .verifying(uriCheckConstraint)
        .transform[URI](
          u => new URI(u),
          u => u.toString
        ),
      "language_tag"  -> optional(text),
      "username"      -> optional(text),
      "password"      -> optional(text),
      "checksum"      -> optional(text),
      "authorisation" -> ResourceAuthorisationFieldsForm.form
    )(Data.apply)(Data.unapply)
  )

  /**
    * A class for the form data.
    *
    * @param uri The URI for connection.
    * @param language_tag Language tag for the source that represents a Locale.
    * @param username An optional username.
    * @param password An optional password.
    * @param checksum An optional SHA-256 checksum.
    * @param authorisation The authorisation fields for the resource.
    */
  final case class Data(
      uri: URI,
      language_tag: Option[String],
      username: Option[String],
      password: Option[String],
      checksum: Option[String],
      authorisation: ResourceAuthorisationFieldsForm.Data
  )

}
