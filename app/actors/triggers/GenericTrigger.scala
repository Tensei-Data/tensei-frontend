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

package actors.triggers

import actors.WorkQueueMaster.WorkQueueMasterMessages
import akka.actor.ActorSelection
import models.WorkQueueEntry

/**
  * A trait that holds generic functions for triggers.
  */
trait GenericTrigger {
  val tcQueueMaster: ActorSelection

  /**
    * Trigger the transformation configuration with the given id by adding it
    * to the queue.
    *
    * @param id The database id of the desired transformation configuration.
    */
  def triggerTransformationConfiguration(id: Long): Unit = {
    val entry = WorkQueueEntry.fromTrigger(id)
    tcQueueMaster ! WorkQueueMasterMessages.AddToQueue(entry)
  }
}
