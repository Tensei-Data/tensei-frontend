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

package modules

import actors._
import actors.triggers.{ CamelTrigger, CompletedTrigger }
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

/**
  * We use a play module annotated as singleton to bind the actors we need upon startup.
  */
class ActorsModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[AgentRunLogsFetcher]("AgentRunLogsFetcher")
    bindActor[CronMaster]("CronMaster")
    bindActor[FrontendService]("FrontendService")
    bindActor[WorkQueueMaster]("WorkQueueMaster")
    bindActor[TriggerMaster]("TriggerMaster")
    // We inject our child actors for triggers via factories provided by Guice.
    bindActorFactory[CamelTrigger, CamelTrigger.Factory]
    bindActorFactory[CompletedTrigger, CompletedTrigger.Factory]
    // Boot the class to make sure the actors are started.
    bind[BootActors](classOf[BootActors]).asEagerSingleton()
  }
}
