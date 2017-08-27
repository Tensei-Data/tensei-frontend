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
  * The Unix-style cron for the Tensei-Data system.
  *
  * @param id          An option on the database ID.
  * @param tkid        The ID of the associated transformation configuration.
  * @param description An optional description of the cron.
  * @param endpointUri The URI to the defined endpoint that will be consumed and watched.
  * @param triggerTkId An option to a transformation configuration id that should trigger instead of the endpoint uri.
  * @param ownerId The owner of the resource.
  * @param groupId The group that owns the resource.
  * @param groupPermissions The permissions for the group.
  * @param worldPermissions The permissions for the world e.g. any user that is not the owner or a member of the resource group.
  */
final case class Trigger(
    id: Option[Long],
    tkid: Long,
    description: Option[String],
    endpointUri: Option[String],
    triggerTkId: Option[Long],
    active: Boolean,
    ownerId: Int,
    groupId: Option[Int],
    groupPermissions: Set[Permission],
    worldPermissions: Set[Permission]
) extends AuthorisableResource
