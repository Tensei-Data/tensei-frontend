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

/**
  * This form handles the creation and editing of a [[models.CookbookResource]].
  */
object CookbookResourceForm {

  // A play framework form definition.
  val form = Form(
    mapping(
      "cookbookId" -> nonEmptyText(minLength = 1, maxLength = 128)
        .verifying("errors.element.name", p => p.matches("[a-zA-Z0-9-]+")),
      "authorisation" -> ResourceAuthorisationFieldsForm.form
    )(Data.apply)(Data.unapply)
  )

  /**
    * A class for the form data.
    *
    * @param cookbookId The ID of the cookbook that is attached to the cookbook resource.
    * @param authorisation The authorisation fields for the resource.
    */
  final case class Data(cookbookId: String, authorisation: ResourceAuthorisationFieldsForm.Data)

}
