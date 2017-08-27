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

import models.Authorities.ResourceAuthority

/**
  * This trait needs to be extended by all resources that need authorisation.
  *
  * Resources have to have an owner, an optional owner group. Furthermore they
  * need to have group and world permissions.
  *
  * Several helper functions are implemented to generate the [[models.Authorities]]
  * for checking the different levels of access (execute, read, write).
  */
trait AuthorisableResource {

  /**
    * The owner of the resource.
    *
    * @return The database id of the user account that owns the resource.
    */
  def ownerId: Int

  /**
    * The group that owns the resource.
    *
    * @return The database id of the group that owns the resource.
    */
  def groupId: Option[Int]

  /**
    * The permissions for members of the group that owns the resource.
    *
    * @return The group permissions.
    */
  def groupPermissions: Set[Permission]

  /**
    * The permissions for the world e.g. any user that is not the owner or
    * a member of the resource group.
    *
    * @return The world permissions.
    */
  def worldPermissions: Set[Permission]

  /**
    * Generate the resource authority that is needed to have execute access
    * to the resource.
    *
    * @return A resource authority that contains only the execute permissions if they are defined.
    */
  def getExecuteAuthorisation = ResourceAuthority(
    ownerId = ownerId,
    groupId = groupId,
    groupPermissions = groupPermissions.filter(_ == Permission.Execute),
    worldPermissions = worldPermissions.filter(_ == Permission.Execute)
  )

  /**
    * Generate the resource authority that is needed to have read access to
    * the resource.
    *
    * @return A resource authority that contains only the read permissions if they are defined.
    */
  def getReadAuthorisation = ResourceAuthority(
    ownerId = ownerId,
    groupId = groupId,
    groupPermissions = groupPermissions.filter(_ == Permission.Read),
    worldPermissions = worldPermissions.filter(_ == Permission.Read)
  )

  /**
    * Generate the resource authority that is needed to have write access to
    * the resource.
    *
    * @return A resource authority that contains only the write permissions if they are defined.
    */
  def getWriteAuthorisation = ResourceAuthority(
    ownerId = ownerId,
    groupId = groupId,
    groupPermissions = groupPermissions.filter(_ == Permission.Write),
    worldPermissions = worldPermissions.filter(_ == Permission.Write)
  )

}
