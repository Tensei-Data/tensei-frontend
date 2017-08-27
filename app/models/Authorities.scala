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

package models

/**
  * A sealed trait for the different authorities.
  *
  * The authorities are defined in the companion object of the trait.
  */
sealed trait Authorities

/**
  * A companion object for the trait to keep the namespace clean.
  */
object Authorities {

  /**
    * This authority requires the user to be logged in as an administrator.
    */
  case object AdminAuthority extends Authorities

  /**
    * This is an authority for authorisable resources.
    *
    * @param ownerId          The database id of the owner of the resource.
    * @param groupId          The database id of the group that owns the resource.
    * @param groupPermissions The permissions for the group.
    * @param worldPermissions The permissions for the world.
    */
  final case class ResourceAuthority(
      ownerId: Int,
      groupId: Option[Int],
      groupPermissions: Set[Permission],
      worldPermissions: Set[Permission]
  ) extends Authorities

  /**
    * This authority requires the user to be logged in as a user.
    */
  case object UserAuthority extends Authorities

  /**
    * This authority requires the user to be logged in as a user with a specific id.
    *
    * @param userId The database id of an user account.
    */
  final case class UserWithIdAuthority(userId: Int) extends Authorities

}
