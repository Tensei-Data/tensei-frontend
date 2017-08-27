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

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }

/**
  * This form handles the creation and editing of triggers.
  */
object TriggerForm {

  /*
   * A custom constraint that checks if a trigger has either endpoint uri or
   * trigger transformation defined and does not produce a direct endless loop.
   */
  val triggerConstraint: Constraint[Data] = Constraint("constraints.trigger.source")({ t =>
    val e1 = t.triggerTransformation.fold(List.empty[ValidationError])(
      id => if (t.tkid == id) List(ValidationError("Triggering endless loop!")) else List.empty
    )
    val e2 = (t.endpointUri, t.triggerTransformation) match {
      case (None, None) =>
        List(
          ValidationError("You must define either an endpoint uri or a trigger transformation!")
        )
      case (Some(uri), Some(id)) =>
        List(
          ValidationError(
            "You cannot define an endpoint uri and a trigger transformation! Please use only one."
          )
        )
      case _ => List.empty
    }
    val es = e1 ::: e2
    if (es.isEmpty)
      Valid
    else
      Invalid(es)
  })

  // A play framework form definition.
  val form = Form(
    mapping(
      "tkid"                  -> longNumber,
      "description"           -> optional(nonEmptyText),
      "endpointUri"           -> optional(nonEmptyText),
      "triggerTransformation" -> optional(longNumber),
      "active"                -> boolean,
      "authorisation"         -> ResourceAuthorisationFieldsForm.form
    )(Data.apply)(Data.unapply).verifying(triggerConstraint)
  )

  /**
    * A class for the form data.
    *
    * @param tkid The database ID of the transformation configuration that shall be executed by the trigger.
    * @param description An optional description of the trigger.
    * @param endpointUri An optional URI that describes an apache camel endpoint that shall activate the trigger.
    * @param triggerTransformation An optional ID of a transformation configuration that shall activate the trigger.
    * @param active Indicates if the trigger is enabled or disabled.
    * @param authorisation The authorisation fields for the resource.
    */
  final case class Data(
      tkid: Long,
      description: Option[String],
      endpointUri: Option[String],
      triggerTransformation: Option[Long],
      active: Boolean,
      authorisation: ResourceAuthorisationFieldsForm.Data
  )

}
