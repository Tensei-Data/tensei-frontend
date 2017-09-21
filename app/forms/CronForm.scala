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

import org.quartz.CronExpression
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }

import scala.util.{ Failure, Success, Try }

/**
  * This form handles the creation and editing of crontab entries.
  */
object CronForm {

  /*
   * A custom constraint to verify the entered cron format.
   */
  val cronFormatConstraint: Constraint[String] = Constraint("constraints.cron-format-check")({
    formatString =>
      Try {
        new CronExpression(formatString)
      } match {
        case Failure(f) => Invalid(Seq(ValidationError(f.getMessage)))
        case Success(_) => Valid
      }
  })

  // A play framework form definition.
  val form = Form(
    mapping(
      "tkid"          -> longNumber,
      "description"   -> optional(nonEmptyText),
      "format"        -> nonEmptyText.verifying(cronFormatConstraint),
      "active"        -> boolean,
      "authorisation" -> ResourceAuthorisationFieldsForm.form
    )(Data.apply)(Data.unapply)
  )

  /**
    * A class for the form data.
    *
    * @param tkid The ID of the transformation configuration that shall be triggered by the cronjob.
    * @param description An optional description of the cronjob.
    * @param format UNIX style format describing the cronjob regarding execution time.
    * @param active Indicates if the cronjob is active or disabled.
    * @param authorisation The authorisation fields for the resource.
    */
  final case class Data(
      tkid: Long,
      description: Option[String],
      format: String,
      active: Boolean,
      authorisation: ResourceAuthorisationFieldsForm.Data
  )

}
