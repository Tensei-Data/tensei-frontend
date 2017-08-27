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

import actors.WorkQueueMaster
import akka.actor.{ Actor, ActorLogging, ActorSelection }
import akka.cluster.pubsub.{ DistributedPubSub, DistributedPubSubMediator }
import com.google.inject.assistedinject.Assisted
import com.wegtam.tensei.adt.GlobalMessages
import dao.{ TransformationConfigurationDAO, WorkHistoryDAO }
import models.{ TransformationConfiguration, WorkHistoryEntry }

import scala.concurrent.Future

/**
  * This trigger subscribes to the `transformationStatus` topic of the distributed pub sub mediator.
  * It executes the desired transformation configuration if a message of the completion of the trigger
  * transformation configuration is received.
  *
  * @param transformationConfigurationDAO The DAO for transformation configurations provided via dependency injection.
  * @param tcQueueHistoryDAO The DAO for the transformation configurations history provided via dependency injection.
  * @param transformationConfigurationID The ID of transformation configuration that shall be triggered.
  * @param triggeringTransformationConfigurationId The ID of the transformation configuration which shall trigger the other one after completion.
  */
class CompletedTrigger @Inject()(
    transformationConfigurationDAO: TransformationConfigurationDAO,
    tcQueueHistoryDAO: WorkHistoryDAO,
    @Assisted("transformationConfigurationID") transformationConfigurationID: Long,
    @Assisted("triggeringTransformationConfigurationID") triggeringTransformationConfigurationId: Long
) extends Actor
    with ActorLogging
    with GenericTrigger {
  import DistributedPubSubMediator.{ Subscribe, SubscribeAck, Unsubscribe }
  import context.dispatcher

  override val tcQueueMaster: ActorSelection =
    context.system.actorSelection(s"/user/${WorkQueueMaster.name}")
  // Get the distributed pub sub mediator and subscribe to the topic for completed transformation configurations.
  private val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(CompletedTrigger.TransformationStatusTopic, self)

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    mediator ! Unsubscribe(CompletedTrigger.TransformationStatusTopic, self)
    super.postStop()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.NonUnitStatements"))
  override def receive: Receive = {
    case SubscribeAck(Subscribe(CompletedTrigger.TransformationStatusTopic, None, `self`)) =>
      context become ready
    case msg =>
      log.warning("Trigger received unhandled trigger message from {}!", sender().path)
      log.debug("Unhandled trigger message: {}", msg)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.NonUnitStatements"))
  def ready: Receive = {
    case GlobalMessages.TransformationCompleted(uuidOpt) =>
      uuidOpt match {
        case None =>
          log.warning("Received transformation completed message without uuid!")
        case Some(uuid) =>
          val tcO: Future[(Option[WorkHistoryEntry], Option[TransformationConfiguration])] = for {
            ho <- tcQueueHistoryDAO.findById(uuid)
            to <- transformationConfigurationDAO.findById(transformationConfigurationID)
            if ho.fold(false)(h => h.tkid == triggeringTransformationConfigurationId)
          } yield (ho, to)
          tcO.foreach {
            case (None, _) =>
              log.error("No history queue entry found for uuid {}!", uuid)
            case (_, None) =>
              log.error("Transformation configuration with id {} not found by trigger!",
                        transformationConfigurationID)
            case (Some(he), Some(tc)) =>
              if (tc.dirty) {
                log.error(
                  "Transformation configuration {} is marked dirty! Will not execute!",
                  transformationConfigurationID
                )
              } else {
                log.info(
                  "Triggering transformation configuration {} after successful completion of {} (run id: {}).",
                  transformationConfigurationID,
                  he.tkid,
                  uuid
                )
                triggerTransformationConfiguration(transformationConfigurationID)
              }
          }
      }
    case msg =>
      log.warning("Received unhandled message! {}", msg)
  }
}

object CompletedTrigger {
  // The topic name for the distributed pub sub mediator regarding transformations.
  val TransformationStatusTopic = "transformationStatus"

  /**
    * We define a factory trait for the actor. The implementation will be provided by Guice.
    */
  trait Factory {

    /**
      * Create an actor that implements a trigger for completed transformation configurations.
      *
      * @param transformationConfigurationID        The id of the transformation configuration that should be started.
      * @param triggeringTransformationConfigurationId The id of the transformation configuration that triggers the execution.
      * @return
      */
    def apply(
        @Assisted("transformationConfigurationID") transformationConfigurationID: Long,
        @Assisted("triggeringTransformationConfigurationID") triggeringTransformationConfigurationId: Long
    ): Actor
  }
}
