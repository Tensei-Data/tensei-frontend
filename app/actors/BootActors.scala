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

package actors

import javax.inject.{ Inject, Named, Singleton }

import akka.actor.{ ActorRef, ActorSystem }
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

@Singleton
class BootActors @Inject()(
    system: ActorSystem,
    @Named("CronMaster") cronMaster: ActorRef,
    @Named("FrontendService") frontendService: ActorRef,
    @Named("WorkQueueMaster") workQueueMaster: ActorRef,
    @Named("TriggerMaster") triggerMaster: ActorRef,
    lifecycle: ApplicationLifecycle
) {

  // Register a stop hook to shutdown our actors.
  lifecycle.addStopHook(() => Future.successful(stopActors()))

  /**
    * Stop all injected actors to avoid crashes upon application shutdown.
    */
  private def stopActors(): Unit = {
    system.stop(cronMaster)
    system.stop(frontendService)
    system.stop(triggerMaster)
    system.stop(workQueueMaster)
  }
}
