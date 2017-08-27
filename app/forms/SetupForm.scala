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
  * This form handles the initial administrator setup.
  */
object SetupForm {

  // A play framework form definition.
  val form = Form(
    mapping(
      "email" -> email,
      "password" -> tuple(
        "main"    -> nonEmptyText(minLength = 12),
        "confirm" -> nonEmptyText(minLength = 12)
      ).verifying("setup.error.passwords-not-matching", password => password._1 == password._2)
    )(Data.apply)(Data.unapply)
  )

  /**
    * A class for the form data.
    *
    * @param email    The email address of the user.
    * @param password The password pair that the user entered.
    */
  final case class Data(email: String, password: (String, String))

}
