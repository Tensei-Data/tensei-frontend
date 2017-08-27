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

import javax.inject.Inject

import actors.FrontendService.FrontendServiceMessages
import actors.WorkQueueMaster
import akka.actor.{ Actor, ActorLogging, ActorSelection }
import akka.camel._
import com.google.inject.assistedinject.Assisted
import dao.TransformationConfigurationDAO

/**
  * This actor handles the triggering of transformation configurations that are triggered
  * via camel endpoints.
  *
  * @param transformationConfigurationDAO The DAO for transformation configurations provided via dependency injection.
  * @param transformationConfigurationID The ID of transformation configuration that shall be triggered.
  * @param endpointUriString The URI of the camel endpoint.
  */
class CamelTrigger @Inject()(
    transformationConfigurationDAO: TransformationConfigurationDAO,
    @Assisted transformationConfigurationID: Long,
    @Assisted endpointUriString: String
) extends Actor
    with ActorLogging
    with Consumer
    with GenericTrigger {
  import context.dispatcher

  override val tcQueueMaster: ActorSelection =
    context.system.actorSelection(s"/user/${WorkQueueMaster.name}")

  override def endpointUri: String = endpointUriString

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.NonUnitStatements"))
  override def receive: Receive = {
    case msg: CamelMessage => handleCamelMessage(msg)
    case FrontendServiceMessages.ServerConnectionError =>
      log.error(
        "Transformation configuration could not be started via trigger. Server connection error."
      )
    case msg =>
      log.warning("Trigger received unhandled trigger message from {}!", sender().path)
      log.debug("Unhandled trigger message: {}", msg)
  }

  def handleCamelMessage(msg: CamelMessage): Unit = {
    log.debug("Received trigger message: {}", msg.getHeaders.toString)
    val receiver = sender()
    transformationConfigurationDAO
      .findById(transformationConfigurationID)
      .foreach(
        o =>
          o.fold(
            log.error("No transformation configuration found with id {}!",
                      transformationConfigurationID)
          ) { tc =>
            if (tc.dirty)
              log.error("Transformation configuration {} is marked dirty! Will not execute!",
                        transformationConfigurationID)
            else
              tc.id.fold(log.error("Transformation configuration contained no ID! {}", tc))(
                id => triggerTransformationConfiguration(id)
              )
        }
      )
    receiver ! Ack
  }
}

object CamelTrigger {

  /**
    * We define a factory trait for the actor. The implementation will be provided by Guice.
    */
  trait Factory {

    /**
      * Create an actor that implements a trigger.
      *
      * @param transformationConfigurationID The database id of the transformation configuration.
      * @param endpointUriString             The uri endpoint that should trigger the transformation configuration.
      * @return An actor.
      */
    def apply(@Assisted transformationConfigurationID: Long,
              @Assisted endpointUriString: String): Actor
  }
}
