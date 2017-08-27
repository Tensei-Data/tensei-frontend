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

import com.wegtam.tensei.adt.DFASDL
import play.api.data.Form
import play.api.data.Forms._

/**
  * This form handles the creation and editing of [[models.DFASDLResource]].
  */
object DfasdlResourceForm {

  // A play framework form definition.
  val form = Form(
    mapping(
      "dfasdl" -> mapping(
        "id" -> nonEmptyText(minLength = 1, maxLength = 200)
          .verifying("Illegal characters in DFASDL name!", p => p.matches("[a-zA-Z0-9-]+")),
        "content" -> nonEmptyText,
        "version" -> nonEmptyText(minLength = 1, maxLength = 32)
      )(DFASDL.apply)(DFASDL.unapply),
      "authorisation" -> ResourceAuthorisationFieldsForm.form
    )(Data.apply)(Data.unapply)
  )

  /**
    * A class for the form data.
    *
    * @param dfasdl A DFASDL.
    * @param authorisation The authorisation fields for the resource.
    */
  final case class Data(dfasdl: DFASDL, authorisation: ResourceAuthorisationFieldsForm.Data)

}
