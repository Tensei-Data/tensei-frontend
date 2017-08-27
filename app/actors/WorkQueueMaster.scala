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

import javax.inject.{ Inject, Singleton }

import actors.WorkQueueMaster.WorkQueueMasterMessages.AddToQueue
import actors.WorkQueueMaster.{ WorkQueueMasterData, WorkQueueMasterMessages, WorkQueueMasterState }
import akka.actor._
import com.typesafe.config.ConfigFactory
import com.wegtam.tensei.adt.{ AgentStartTransformationMessage, DFASDLReference }
import com.wegtam.tensei.server.ServerMessages
import dao._
import models.{
  ConnectionInformationResource,
  CookbookResource,
  DFASDLResource,
  TransformationConfiguration,
  WorkHistoryEntry,
  WorkQueueEntry
}

import scala.concurrent.Future
import scala.concurrent.duration._
import scalaz._

/**
  * This actor manages the queue of transformation configurations. The queue contains
  * configurations that shall be executed by the next free agent.
  *
  * @todo This actor needs refactoring and should be broken up into multiple pieces to reflect the asynchronous event handling.
  * @param workQueueDAO The DAO for the transformation configuration queue provided via dependency injection.
  * @param workHistoryDAO The DAO for handling the transformation configuration history provided via dependency injection.
  * @param connectionInformationDAO The DAO for connection informations provided via dependency injection.
  * @param cookbookResourceDAO The DAO for cookbook resources provided via dependency injection.
  * @param transformationConfigurationDAO The DAO for transformation configurations provided via dependency injection.
  */
@Singleton
class WorkQueueMaster @Inject()(
    workQueueDAO: WorkQueueDAO,
    workHistoryDAO: WorkHistoryDAO,
    connectionInformationDAO: ConnectionInformationResourceDAO,
    cookbookResourceDAO: CookbookResourceDAO,
    dfasdlResourceDAO: DFASDLResourceDAO,
    transformationConfigurationDAO: TransformationConfigurationDAO
) extends Actor
    with FSM[WorkQueueMasterState, WorkQueueMasterData]
    with ActorLogging {
  import context.dispatcher

  private val frontendSelection = context.system.actorSelection(s"/user/${FrontendService.name}")
  private val queueConfig       = ConfigFactory.load("tensei.conf").getConfig("tensei.queue")

  val startTimer =
    FiniteDuration(queueConfig.getDuration("start-interval", MILLISECONDS), MILLISECONDS)

  val startingTimeout =
    FiniteDuration(queueConfig.getDuration("starting-timeout", MILLISECONDS), MILLISECONDS)

  setTimer("StartTimer", WorkQueueMasterMessages.NextEntry, startTimer, repeat = true)

  log.info("WorkQueueMaster service started at {}.", self.path)

  startWith(WorkQueueMasterState.Idle, WorkQueueMasterData())

  when(WorkQueueMasterState.Idle) {
    case Event(WorkQueueMasterMessages.NextEntry, data) =>
      log.debug("Reached queue starting timer.")
      val _ = startNextEntry(data)
      stay() using WorkQueueMasterData()
    case Event(WorkQueueMasterMessages.AddToQueue(entry), data) =>
      log.debug("Received add to queue request {}.", entry)
      val mySelf = self
      val _ = for {
        we <- workQueueDAO.create(entry)
        he <- we match {
          case scala.util.Failure(t) =>
            log.error(t, "Could not create work queue entry!")
            Future.successful(scala.util.Failure(t))
          case scala.util.Success(e) => workHistoryDAO.create(WorkHistoryEntry.fromQueueEntry(e))
        }
      } yield
        he match {
          case scala.util.Failure(t) => log.error(t, "Could not create work history entry!")
          case scala.util.Success(e) =>
            log.debug("Created queue and history entry {}.", e.uuid)
            mySelf ! WorkQueueMasterMessages.NextEntry // TODO We should avoid sending ourself a message here to trigger the just received entry.
        }
      stay() using WorkQueueMasterData()
    case Event(WorkQueueMasterMessages.SwitchToWatchingState, data) =>
      goto(WorkQueueMasterState.Watching) using data
  }

  when(WorkQueueMasterState.Watching, stateTimeout = startingTimeout) {
    case Event(ServerMessages.StartTransformationConfigurationResponse(message, uniqueIdentifier),
               data) =>
      log.info("Got response from server for queue starting one entry: {}", message)
      message match {
        case -\/(error) =>
          log.error("Server could not start queue entry: {}", error.message)
          error.cause.foreach(c => log.error("Caused by: {}", c.message))
          goto(WorkQueueMasterState.Idle) using WorkQueueMasterData()
        case \/-(status) =>
          // Remove the agent from the active queue
          uniqueIdentifier.foreach(uuid => workQueueDAO.destroy(uuid))
          goto(WorkQueueMasterState.Idle) using WorkQueueMasterData()
      }
    case Event(StateTimeout, data) =>
      log.info("Queue starting timeout fired.")
      goto(WorkQueueMasterState.Idle) using WorkQueueMasterData()
    case msg =>
      log.error("Got unhandled message! {}", msg)
      goto(WorkQueueMasterState.Idle) using WorkQueueMasterData()
  }

  whenUnhandled {
    case Event(AddToQueue(entry), data) =>
      val _ = for {
        we <- workQueueDAO.create(entry)
        he <- we match {
          case scala.util.Failure(t) =>
            log.error(t, "Could not create work queue entry!")
            Future.successful(scala.util.Failure(t))
          case scala.util.Success(e) => workHistoryDAO.create(WorkHistoryEntry.fromQueueEntry(e))
        }
      } yield
        he match {
          case scala.util.Failure(t) => log.error(t, "Could not create work history entry!")
          case scala.util.Success(e) => log.debug("Created queue and history entry {}.", e.uuid)
        }
      stay() using data
  }

  initialize()

  def startNextEntry(data: WorkQueueMasterData): Future[Unit] = {
    log.debug("Starting next work queue entry. {}", data)
    val mySelf = self
    for {
      o <- workQueueDAO.getNextEntry
      to <- o.fold(Future.successful(None: Option[TransformationConfiguration]))(
        e => transformationConfigurationDAO.findById(e.tkid)
      )
      co <- to.fold(Future.successful(None: Option[CookbookResource]))(
        t =>
          t.cookbook.id.fold(Future.successful(None: Option[CookbookResource]))(
            cid => cookbookResourceDAO.findById(cid)(loadCookbook = true)
        )
      )
      dfasdls <- to.fold(Future.successful(Seq.empty[DFASDLResource])) { tc =>
        val ids: Set[Long] = Set(tc.targetConnection.dfasdlId) ++ tc.sourceConnections
          .map(_.dfasdlId)
          .toSet
        dfasdlResourceDAO.findByIds(ids)(loadDfasdl = true)
      }
      connections <- to.fold(Future.successful(Seq.empty[ConnectionInformationResource])) { tc =>
        val ids
          : Set[Long] = Set(tc.targetConnection.connectionInformationId) ++ tc.sourceConnections
          .map(_.connectionInformationId)
          .toSet
        connectionInformationDAO.findByIds(ids)
      }
    } yield {
      o.fold(log.debug("No next work queue entry."))(
        entry =>
          to.fold(
            log.error("No transformation configuration found for work queue entry {}.", entry.uuid)
          ) { tc =>
            if (tc.dirty)
              log.error("Transformation configuration {} is marked dirty! Will not execute!", tc.id)
            else
              co.fold(
                log.error("Cookbook for transformation configuration {} not found!", tc.id)
              ) {
                cookbookResource =>
                  log.debug(
                    "Constructing necessary agent informations from transformation configuration {} and cookbook {}",
                    tc.id,
                    tc.cookbook.id
                  )
                  val cookbookId = cookbookResource.cookbook.id
                  val target = {
                    val targetDfasdl =
                      dfasdls.find(_.id.contains(tc.targetConnection.dfasdlId)).get
                    val targetConnection = connections
                      .find(_.id.contains(tc.targetConnection.connectionInformationId))
                      .get
                    targetConnection.connection.copy(
                      dfasdlRef = Option(
                        DFASDLReference(cookbookId = cookbookId, dfasdlId = targetDfasdl.dfasdl.id)
                      )
                    )
                  }
                  log.debug("Constructed target connection information {}.", target)
                  val sources = tc.sourceConnections.map { sc =>
                    val c = connections.find(_.id.contains(sc.connectionInformationId)).get
                    val d = dfasdls.find(_.id.contains(sc.dfasdlId)).get
                    c.connection.copy(
                      dfasdlRef =
                        Option(DFASDLReference(cookbookId = cookbookId, dfasdlId = d.dfasdl.id))
                    )
                  }
                  log.debug("Constructed {} source connection informations.", sources.size)
                  log.debug("Source connection informations: {}", sources)
                  log.debug("Cookbook used for agent: {}", cookbookResource.cookbook)
                  val msg = AgentStartTransformationMessage(
                    sources = sources.toList,
                    target = target,
                    cookbook = cookbookResource.cookbook,
                    uniqueIdentifier = Option(entry.uuid)
                  )
                  frontendSelection ! WorkQueueMasterMessages.StartMessage(msg, self)
                  log.debug(
                    "Started transformation configuration {} from queue with identifier {}.",
                    entry.tkid,
                    entry.uuid
                  )
                  mySelf ! WorkQueueMasterMessages.SwitchToWatchingState // Instruct ourself to switch to watching state.
              }
        }
      )
    }
  }
}

object WorkQueueMaster {
  def props: Props = Props(classOf[WorkQueueMaster])

  sealed trait WorkQueueMasterState

  object WorkQueueMasterState {

    case object Idle extends WorkQueueMasterState

    case object Starting extends WorkQueueMasterState

    case object Watching extends WorkQueueMasterState
  }

  final case class WorkQueueMasterData()

  sealed trait WorkQueueMasterMessages

  object WorkQueueMasterMessages {
    final case class StartMessage(message: AgentStartTransformationMessage, ref: ActorRef)
        extends WorkQueueMasterMessages

    case object SwitchToWatchingState extends WorkQueueMasterMessages

    case object NextEntry extends WorkQueueMasterMessages

    final case class AddToQueue(entry: WorkQueueEntry) extends WorkQueueMasterMessages
  }

  val name = "WorkQueueMaster"
}
