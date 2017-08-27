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
  * This form handles updating the profile data for an account.
  */
object AccountForms {

  // A play framework form definition.
  val adminForm = Form(
    mapping(
      "email" -> tuple(
        "main"    -> email,
        "confirm" -> email
      ).verifying("account.error.emails-not-matching", email => email._1 == email._2),
      "isAdmin" -> boolean
    )(AdminData.apply)(AdminData.unapply)
  )

  // A play framework form definition.
  val groupForm = Form(
    mapping(
      "name" -> nonEmptyText
    )(GroupData.apply)(GroupData.unapply)
  )

  // A play framework form definition.
  val profileForm = Form(
    mapping(
      "email" -> tuple(
        "main"    -> email,
        "confirm" -> email
      ).verifying("account.error.emails-not-matching", email => email._1 == email._2)
    )(ProfileData.apply)(ProfileData.unapply)
  )

  /**
    * A class for the form data.
    *
    * @param email The email address of the user.
    * @param isAdmin A flag that indicates if the user is an administrator.
    */
  final case class AdminData(email: (String, String), isAdmin: Boolean)

  /**
    * A class for the form data.
    *
    * @param name The name of the user group.
    */
  final case class GroupData(name: String)

  /**
    * A class for the form data.
    *
    * @param email The email address of the user.
    */
  final case class ProfileData(email: (String, String))

}
