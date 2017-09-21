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

import models.Permission
import play.api.data.Forms._
import play.api.data.Mapping

/**
  * This form is actually a helper form that can be used to ease the inclusion of the
  * permission fields in forms.
  */
object ResourceAuthorisationFieldsForm {
  // A constant that should be used to name the key to the subform that is used for authorisation.
  val SubFormName = "authorisation"

  // A play framework form mapping to be used in another form.
  val form: Mapping[Data] = mapping(
    "ownerId" -> number,
    "groupId" -> optional(number),
    "groupPermissions" -> number.transform[Set[Permission]](
      e => Permission.decode(e),
      d => Permission.encodeSet(d)
    ),
    "worldPermissions" -> number.transform[Set[Permission]](
      e => Permission.decode(e),
      d => Permission.encodeSet(d)
    )
  )(Data.apply)(Data.unapply)

  /**
    * A class for the form data.
    *
    * @param ownerId The owner of the resource.
    * @param groupId The group that owns the resource.
    * @param groupPermissions The permissions for the group.
    * @param worldPermissions The permissions for the world e.g. any user that is not the owner or a member of the resource group.
    */
  final case class Data(
      ownerId: Int,
      groupId: Option[Int],
      groupPermissions: Set[Permission],
      worldPermissions: Set[Permission]
  )

}
