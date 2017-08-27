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

import javax.inject.Inject

import actors.TriggerMaster.TriggerMasterMessages.{ InitializeTriggers, UpdateTrigger }
import actors.triggers.{ CamelTrigger, CompletedTrigger }
import akka.actor._
import dao.TriggerDAO
import models.Trigger
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

/**
  * This actor manages all active triggers. These are child actors of this actor.
  * Upon startup all triggers are initialised (started up) and updated (stopped and re-started)
  * if needed.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param triggerDAO The DAO for managing triggers provided via dependency injection.
  * @param camelTriggerFactory A factory class to create camel based trigger actors provided via dependency injection.
  * @param completedTriggerFactor A factory class to create triggers that are based on completed transformation runs provided via dependency injection.
  */
class TriggerMaster @Inject()(protected val configuration: Configuration,
                              triggerDAO: TriggerDAO,
                              camelTriggerFactory: CamelTrigger.Factory,
                              completedTriggerFactor: CompletedTrigger.Factory)
    extends Actor
    with ActorLogging
    with InjectedActorSupport {
  import context.dispatcher

  val DEFAULT_DELAY = 500L // The fallback default initial delay for the trigger service in milliseconds.

  // TODO Get rid of the state map here and solve this via `children`!
  val activeTriggers: mutable.HashMap[ActorRef, Long] = new mutable.HashMap[ActorRef, Long]()

  log.info("TriggerMaster service started at {}.", self.path)

  private val delay = FiniteDuration(
    configuration.getMilliseconds("tensei.frontend.triggers.init-delay").getOrElse(DEFAULT_DELAY),
    MILLISECONDS
  )
  private val initTimer = context.system.scheduler.scheduleOnce(delay, self, InitializeTriggers)

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    initTimer.cancel()
    super.postStop()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.NonUnitStatements"))
  override def receive: Receive = {
    case InitializeTriggers =>
      log.info("Initialising triggers...")
      initializeTriggers().foreach(cnt => log.info("Initialised {} triggers.", cnt))

    case UpdateTrigger(id) =>
      log.debug("Got update message for trigger with id: {}", id)
      activeTriggers.find(_._2 == id) match {
        case None =>
          // No trigger actor is running for the given id, therefore we create one.
          triggerDAO.findActiveById(id).foreach(_.foreach(t => createTriggerActor(t)))
        case Some((ref, _)) =>
          // A trigger is already running. We stop it and the death watch will ensure that it is restarted.
          context.stop(ref)
      }

    case Terminated(ref) =>
      log.debug("Got termination message for trigger with actorRef: {}", ref)
      activeTriggers.get(ref) match {
        case None =>
          log.debug("Terminated actor ref was not in active triggers list.")
        case Some(id) =>
          triggerDAO.findActiveById(id).foreach(_.foreach(t => createTriggerActor(t)))
      }
  }

  /**
    * Initialise all triggers found in the database.
    *
    * @return A future holding the number of successfully initialised triggers.
    */
  private def initializeTriggers(): Future[Int] =
    triggerDAO.allActive.map(
      ts =>
        ts.foldLeft(0) { (left, right) =>
          val add = createTriggerActor(right).fold(0)(ref => 1)
          left + add
      }
    )

  /**
    * Create an actor for the given trigger definition.
    *
    * This method also adds the created actor to the `activeTriggers` map and
    * puts a deathwatch on it. Occuring errors are logged and the return of a
    * `None` means that no actor could be created (error).
    *
    * @param t A trigger definition.
    * @return An option to the created actor ref.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.NonUnitStatements"))
  private def createTriggerActor(t: Trigger): Option[ActorRef] =
    t.id.fold {
      log.error("Encountered trigger entry without an ID!")
      None: Option[ActorRef]
    } { triggerId =>
      Try {
        val triggerActor = t.endpointUri.fold {
          t.triggerTkId.fold {
            log.error(
              "Trigger {} has neither endpoint uri nor source trigger transformation configuration defined!",
              t.id
            )
            None: Option[ActorRef]
          } { triggerTcId =>
            Option(
              injectedChild(completedTriggerFactor(t.tkid, triggerTcId),
                            s"Trigger-${t.id.getOrElse("UNDEFINED-TRIGGER-ID")}")
            )
          }
        } { uri =>
          Option(
            injectedChild(camelTriggerFactory(t.tkid, uri),
                          s"Trigger-${t.id.getOrElse("UNDEFINED-TRIGGER-ID")}")
          )
        }
        triggerActor.foreach { ref =>
          activeTriggers.put(ref, triggerId)
          context.watch(ref)
        }
        triggerActor
      } match {
        case Failure(f) =>
          log.error(f, "An error occured while trying to initialise the trigger {}!", triggerId)
          None
        case Success(ref) =>
          log.info("Successfully initialised trigger {}.", triggerId)
          ref
      }
    }
}

object TriggerMaster {
  def props: Props = Props(classOf[TriggerMaster])

  sealed trait TriggerMasterMessages

  object TriggerMasterMessages {
    case class UpdateTrigger(id: Long) extends TriggerMasterMessages

    case object InitializeTriggers extends TriggerMasterMessages
  }

  val Name = "TriggerMaster"
}
