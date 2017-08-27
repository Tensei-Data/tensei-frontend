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

import java.time.LocalDateTime
import javax.inject.Inject

import actors.FrontendService.{ FrontendServiceData, FrontendServiceMessages, FrontendServiceState }
import actors.WorkQueueMaster.WorkQueueMasterMessages
import actors.triggers.CompletedTrigger
import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.pattern.ask
import akka.util.Timeout
import com.wegtam.tensei.adt.GlobalMessages.ErrorOccured
import com.wegtam.tensei.adt.StatsMessages.CalculateStatistics
import com.wegtam.tensei.adt._
import com.wegtam.tensei.server.ServerMessages
import com.wegtam.tensei.server.suggesters.{ MappingSuggesterMessages, MappingSuggesterModes }
import dao.{ WorkHistoryDAO, WorkQueueDAO }
import play.api.Configuration

import scala.concurrent.Future
import scala.concurrent.duration._
import scalaz.Scalaz._

/**
  * This actor provides communication facilities for interacting with the
  * Tensei server component (Chef de Cuisine) and the agents.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param tcQueueDAO The DAO for the transformation configuration queue provided via dependency injection.
  * @param tcQueueHistoryDAO The DAO for the transformation configuration history provided via dependency injection.
  */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class FrontendService @Inject()(protected val configuration: Configuration,
                                tcQueueDAO: WorkQueueDAO,
                                tcQueueHistoryDAO: WorkHistoryDAO)
    extends Actor
    with FSM[FrontendServiceState, FrontendServiceData]
    with ActorLogging {
  import context.dispatcher

  val DEFAULT_SERVER_TIMEOUT         = 5000L  // The fallback default server connection timeout in milliseconds.
  val DEFAULT_SERVER_STARTUP_TIMEOUT = 30000L // The fallback default timeout for the server startup.
  val DEFAULT_TIMEOUT                = 5000L  // The fallback default timeout for `ask` operations.
  val DEFAULT_AGENT_UPDATE_INTERVAL  = 3000L  // The fallback default update interval for agent informations.

  val serverConnectTimeout = FiniteDuration(
    configuration
      .getMilliseconds("tensei.frontend.server-connect-timeout")
      .getOrElse(DEFAULT_SERVER_TIMEOUT),
    MILLISECONDS
  )
  val askTimeout = FiniteDuration(
    configuration.getMilliseconds("tensei.frontend.ask-timeout").getOrElse(DEFAULT_TIMEOUT),
    MILLISECONDS
  )
  val infoUpdateInterval = FiniteDuration(
    configuration
      .getMilliseconds("tensei.frontend.agent-information-update-interval")
      .getOrElse(DEFAULT_AGENT_UPDATE_INTERVAL),
    MILLISECONDS
  )

  val connectTimerName = "CONNECT-TO-SERVER-TIMEOUT"

  setTimer(connectTimerName, StateTimeout, serverConnectTimeout)

  private val mediator = DistributedPubSub(context.system).mediator

  log.info("Frontend service started at {}.", self.path)

  override def preStart(): Unit = {
    connectToChefDeCuisine(None)
    super.preStart()
  }

  override def postStop(): Unit =
    super.postStop()

  startWith(FrontendServiceState.Connecting, FrontendServiceData.initialise)

  when(FrontendServiceState.Connecting, stateTimeout = serverConnectTimeout) {
    case Event(GlobalMessages.ReportingTo(serverRef, serverId), data) =>
      if (serverRef.path.name == ClusterConstants.topLevelActorNameOnServer) {
        log.info("Server reporting in.")
        if (isTimerActive(connectTimerName)) cancelTimer(connectTimerName)
        context.watch(serverRef)
        goto(FrontendServiceState.Running) using data.copy(chef = Option(serverRef))
      } else {
        log.warning("Got unhandled ReportingTo message from '{}'!", serverRef.path)
        stay() using data
      }
    case Event(FrontendServiceMessages.HasServerConnection, data) =>
      log.debug("Request whether the server connection is established in Connecting")
      sender() ! FrontendServiceMessages.ServerDisconnected
      stay() using data
    case Event(StateTimeout, data) =>
      log.warning("Could not connect to server in time. Re-trying.")
      if (isTimerActive(connectTimerName)) cancelTimer(connectTimerName)
      connectToChefDeCuisine(None)
      setTimer(connectTimerName, StateTimeout, serverConnectTimeout)
      goto(FrontendServiceState.Connecting) using data
  }

  when(FrontendServiceState.Running) {
    case Event(TenseiLicenseMessages.ReportLicenseMetaData, data) =>
      log.debug("Request for getting the license meta data")
      data.chef.fold(
        sender() ! ErrorOccured(
          StatusMessage(reporter = Option(self.path.toString),
                        message = "Server unavilable",
                        cause = None)
        )
      )(
        chef => chef.forward(TenseiLicenseMessages.ReportLicenseMetaData)
      )
      stay() using data
    case Event(TenseiLicenseMessages.ReportLicenseEntitiesData, data) =>
      log.debug("Request for getting the license entities data")
      data.chef.fold(
        sender() ! ErrorOccured(
          StatusMessage(reporter = Option(self.path.toString),
                        message = "Server unavilable",
                        cause = None)
        )
      )(
        chef => chef.forward(TenseiLicenseMessages.ReportLicenseEntitiesData)
      )
      stay() using data
    case Event(TenseiLicenseMessages.ValidateLicense(license), data) =>
      log.debug("Request for validating license")
      data.chef.fold(
        sender() ! ErrorOccured(
          StatusMessage(reporter = Option(self.path.toString),
                        message = "Server unavilable",
                        cause = None)
        )
      )(
        chef => chef.forward(TenseiLicenseMessages.ValidateLicense(license))
      )
      stay() using data
    case Event(TenseiLicenseMessages.UpdateLicense(license), data) =>
      log.debug("Request for updating license")
      data.chef.fold(
        sender() ! ErrorOccured(
          StatusMessage(reporter = Option(self.path.toString),
                        message = "Server unavilable",
                        cause = None)
        )
      )(
        chef => chef.forward(TenseiLicenseMessages.UpdateLicense(license))
      )
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfAgents, data) =>
      log.debug("Request for number of allowed agents.")
      data.chef.fold(
        sender() ! ErrorOccured(
          StatusMessage(reporter = Option(self.path.toString),
                        message = "Server unavilable",
                        cause = None)
        )
      )(
        chef => chef.forward(TenseiLicenseMessages.ReportAllowedNumberOfAgents)
      )
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfConfigurations, data) =>
      log.debug("Request for number of allowed configurations.")
      data.chef.fold(
        sender() ! ErrorOccured(
          StatusMessage(reporter = Option(self.path.toString),
                        message = "Server unavilable",
                        cause = None)
        )
      )(
        chef => chef.forward(TenseiLicenseMessages.ReportAllowedNumberOfConfigurations)
      )
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfCronjobs, data) =>
      log.debug("Request for number of allowed cronjobs.")
      data.chef.fold(
        sender() ! ErrorOccured(
          StatusMessage(reporter = Option(self.path.toString),
                        message = "Server unavilable",
                        cause = None)
        )
      )(
        chef => chef.forward(TenseiLicenseMessages.ReportAllowedNumberOfCronjobs)
      )
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfTriggers, data) =>
      log.debug("Request for number of allowed triggers.")
      data.chef.fold(
        sender() ! ErrorOccured(
          StatusMessage(reporter = Option(self.path.toString),
                        message = "Server unavilable",
                        cause = None)
        )
      )(
        chef => chef.forward(TenseiLicenseMessages.ReportAllowedNumberOfTriggers)
      )
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfUsers, data) =>
      log.debug("Request for number of allowed users.")
      data.chef.fold(
        sender() ! ErrorOccured(
          StatusMessage(reporter = Option(self.path.toString),
                        message = "Server unavilable",
                        cause = None)
        )
      )(
        chef => chef.forward(TenseiLicenseMessages.ReportAllowedNumberOfUsers)
      )
      stay() using data
    case Event(FrontendServiceMessages.HasServerConnection, data) =>
      log.debug("Request whether the server connection is established")
      sender() ! FrontendServiceMessages.ServerConnected
      stay() using data
    case Event(FrontendServiceMessages.Connect, data) =>
      log.info("Got connect message, trying to establish server connection.")
      goto(FrontendServiceState.Connecting) using data
    case Event(FrontendServiceMessages.StopTransformation(agent), data) =>
      log.debug("Got abort transformation request.")
      agent ! GlobalMessages.AbortTransformation(sender())
      stay() using data
    case Event(msg: CalculateStatistics, data) =>
      data.chef.fold()(
        chef => chef.forward(msg)
      )
      stay() using data
    case Event(FrontendServiceMessages.MappingSuggesterAdvancedSemanticRequest(cookbook), data) =>
      log.debug("Got advanced semantic suggest mapping message.")
      data.chef.fold(log.error("No server connection available!"))(
        chef =>
          cookbook.fold(log.error("Got no cookbook in advanced semantic suggest mapping request"))(
            cb =>
              chef ! MappingSuggesterMessages
                .SuggestMapping(cb, MappingSuggesterModes.AdvancedSemantics, Option(sender()))
        )
      )
      stay() using data
    case Event(FrontendServiceMessages.MappingSuggesterSemanticRequest(cookbook), data) =>
      log.debug("Got semantic suggest mapping message.")
      data.chef.fold(log.error("No server connection available!"))(
        chef =>
          cookbook.fold(log.error("Got no cookbook in semantic suggest mapping request"))(
            cb =>
              chef ! MappingSuggesterMessages.SuggestMapping(cb,
                                                             MappingSuggesterModes.SimpleSemantics,
                                                             Option(sender()))
        )
      )
      stay() using data
    case Event(FrontendServiceMessages.MappingSuggesterSimpleRequest(cookbook), data) =>
      log.debug("Got suggest mapping message.")
      data.chef.fold(log.error("No server connection available!"))(
        chef =>
          cookbook.fold(log.error("Got no cookbook in suggest mapping request"))(
            cb =>
              chef ! MappingSuggesterMessages.SuggestMapping(cb,
                                                             MappingSuggesterModes.Simple,
                                                             Option(sender()))
        )
      )
      stay() using data
    case Event(FrontendServiceMessages.AgentsInformations(receiver), data) =>
      val returnActor = receiver.getOrElse(sender())
      val service     = self
      data.chef.fold {
        log.error("No server connection available!")
        returnActor ! FrontendServiceMessages.ServerConnectionError("No server connection!")
      } { chef =>
        val lastUpdateInterval = System.currentTimeMillis() - data.lastAgentsInformationsUpdate
        if (data.lastAgentsInformations.nonEmpty && lastUpdateInterval <= infoUpdateInterval.toMillis) {
          log.debug("Using last buffered agents informations.")
          log.debug("Relaying report agents reponse to '{}'.", returnActor.path)
          returnActor ! FrontendServiceMessages.AgentsInformationsResponse(
            data.lastAgentsInformations
          )
        } else {
          log.debug("Querying chef for agents informations.")
          implicit val timeout = Timeout(askTimeout)
          val askChef = chef
            .ask(ServerMessages.ReportAgentsInformations)
            .mapTo[ServerMessages.ReportAgentsInformationsResponse]
          askChef.foreach { msg =>
            log.debug("Relaying report agents reponse to '{}'.", returnActor.path)
            returnActor ! FrontendServiceMessages.AgentsInformationsResponse(msg.agents)
            service ! msg // Relay the message to ourselfs for buffering.
          }
        }
      }
      stay() using data
    case Event(ServerMessages.ReportAgentsInformationsResponse(agents), data) =>
      log.debug("Buffering received agents informations response.")
      stay() using data.copy(lastAgentsInformations = agents,
                             lastAgentsInformationsUpdate = System.currentTimeMillis())
    case Event(WorkQueueMasterMessages.StartMessage(agentStartTransformation, queueRef), data) =>
      data.chef.fold(log.error("No server connection available!")) { chef =>
        val returnActor      = queueRef
        implicit val timeout = Timeout(askTimeout)
        val askChef = chef
          .ask(ServerMessages.StartTransformationConfiguration(Option(agentStartTransformation)))
          .mapTo[ServerMessages.StartTransformationConfigurationResponse]
        askChef.foreach { msg =>
          log.debug("Relaying start transformation configuration response for WorkQueueMaster.")
          returnActor ! ServerMessages.StartTransformationConfigurationResponse(
            msg.statusMessage,
            msg.uniqueIdentifier
          )
        }
      }
      stay() using data
    case Event(FrontendServiceMessages.ExtractSchema(connectionInformation, options), data) =>
      data.chef.fold {
        log.error("No server connection available!")
        sender() ! GlobalMessages.ExtractSchemaResult(connectionInformation,
                                                      "No server connection!".left[DFASDL])
      } { chef =>
        log.debug("Forwarding extract schema message.")
        chef.forward(GlobalMessages.ExtractSchema(connectionInformation, options))
      }
      stay() using data
    case Event(msg: GlobalMessages.RequestAgentRunLogsMetaData, data) =>
      if (data.chef.isEmpty) log.error("No server connection!")
      data.chef.foreach(c => c.forward(msg))
      stay() using data
    case Event(msg: GlobalMessages.RequestAgentRunLogs, data) =>
      if (data.chef.isEmpty) log.error("No server connection!")
      data.chef.foreach(c => c.forward(msg))
      stay() using data
  }

  whenUnhandled {
    case Event(Terminated(ref), data) =>
      data.chef.fold {
        // If we have no server connection then we always jump to the connecting state!
        log.error("No server connection available!")
        goto(FrontendServiceState.Connecting) using data.copy(chef = None)
      } { chef =>
        if (chef == ref || ref.path.name == ClusterConstants.topLevelActorNameOnServer) {
          log.error("Got termination message from server at '{}'! Scheduling reconnect!", ref.path)
          goto(FrontendServiceState.Connecting) using data.copy(chef = None)
        } else if (data.reportAgentsInformationsTo.contains(ref)) {
          log.debug("Removing terminated actor at '{}' from reportAgentsInformationsTo", ref.path)
          stay() using data.copy(
            reportAgentsInformationsTo = data.reportAgentsInformationsTo.filterNot(_ == ref)
          )
        } else
          stay() using data
      }
    case Event(FrontendServiceMessages.Shutdown, data) =>
      log.warning("Received shutdown signal from {}.", sender().path)
      log.warning("Shutting down.")
      stop(FSM.Shutdown)
    case Event(ServerMessages.ReportAgentsInformationsResponse(agents), data) =>
      log.debug("Relaying agents informations message to {} recipients.",
                data.reportAgentsInformationsTo.size)
      data.reportAgentsInformationsTo foreach (ref => {
        ref ! FrontendServiceMessages.AgentsInformationsResponse(agents)
      })
      stay() using data.copy(lastAgentsInformations = agents,
                             lastAgentsInformationsUpdate = System.currentTimeMillis())
    case Event(msg: GlobalMessages.TransformationCompleted, data) =>
      msg.uuid.fold(Future.successful(0)) { uuid =>
        log.debug("Got TransformationCompleted message for uuid: {}", uuid)
        mediator ! Publish(CompletedTrigger.TransformationStatusTopic, msg) // Distribute the message for triggers.
        for {
          e <- tcQueueHistoryDAO.findById(uuid)
          h <- e.fold(Future.successful(0))(
            h =>
              tcQueueHistoryDAO.update(
                h.copy(finished = Option(java.sql.Timestamp.valueOf(LocalDateTime.now())),
                       completed = true)
            )
          )
        } yield h
      }
      stay() using data
    case Event(msg: GlobalMessages.TransformationAborted, data) =>
      msg.uuid.fold(Future.successful(0)) { uuid =>
        log.debug("Got TransformationAborted message for uuid: {}", uuid)
        for {
          e <- tcQueueHistoryDAO.findById(uuid)
          h <- e.fold(Future.successful(0))(
            h =>
              tcQueueHistoryDAO.update(
                h.copy(finished = Option(java.sql.Timestamp.valueOf(LocalDateTime.now())),
                       aborted = true)
            )
          )
        } yield h
      }
      stay() using data
    case Event(msg: GlobalMessages.TransformationStarted, data) =>
      msg.uuid.fold(Future.successful(0)) { uuid =>
        log.info("Got TransformationStarted message for uuid: {}", uuid)
        for {
          e <- tcQueueHistoryDAO.findById(uuid)
          h <- e.fold(Future.successful(0))(
            h =>
              tcQueueHistoryDAO
                .update(h.copy(started = Option(java.sql.Timestamp.valueOf(LocalDateTime.now()))))
          )
        } yield h
      }
      stay() using data
    case Event(msg: GlobalMessages.TransformationError, data) =>
      msg.uuid.fold(Future.successful(0)) { uuid =>
        log.info("Got TransformationError message for uuid: {}", uuid)
        for {
          e <- tcQueueHistoryDAO.findById(uuid)
          h <- e.fold(Future.successful(0))(
            h =>
              tcQueueHistoryDAO.update(
                h.copy(finished = Option(java.sql.Timestamp.valueOf(LocalDateTime.now())),
                       failed = true,
                       message = Option(msg.error.message))
            )
          )
        } yield h
      }
      stay() using data
    case Event(message: Any, data) =>
      log.warning("Got unhandled message from '{}' in state '{}': {}",
                  sender().path,
                  stateName,
                  message)
      stay() using data
  }

  // format: OFF
  onTransition {
    case _ -> FrontendServiceState.Connecting =>
      log.debug("Switching to connecting state, trying to connect to server.")
      if (isTimerActive(connectTimerName)) cancelTimer(connectTimerName)
      connectToChefDeCuisine(None)
      setTimer(connectTimerName, StateTimeout, serverConnectTimeout)
  }
  // format: ON

  initialize()

  /**
    * Tries to establish a connection to the chef actor.
    *
    * @param reportToRef An option to another actor ref that should receive the response from chef or error messages.
    * @return A future to the actor ref of the chef de cuisine.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def connectToChefDeCuisine(reportToRef: Option[ActorRef]): Future[ActorRef] = {
    val seedNodes = configuration.getStringList("tensei.frontend.akka.cluster.seed-nodes")
    if (seedNodes.isEmpty || seedNodes.exists(_.isEmpty)) {
      val error = "No seed nodes defined in frontend configuration!"
      log.error(error)
      reportToRef.foreach(ref => ref ! FrontendServiceMessages.ServerConnectionError(error))
      throw new IllegalArgumentException(error)
    } else {
      seedNodes.map { nodes =>
        val selection = context.actorSelection(
          s"${nodes.get(0)}/user/${ClusterConstants.topLevelActorNameOnServer}"
        )
        implicit val timeout = Timeout(
          FiniteDuration(configuration
                           .getMilliseconds("tensei.frontend.server-startup-timeout")
                           .getOrElse(DEFAULT_SERVER_STARTUP_TIMEOUT),
                         MILLISECONDS)
        )
        val reporter = reportToRef.getOrElse(self)

        import context.dispatcher

        selection.resolveOne().map { ref =>
          log.info("Found chef de cuisine at {}.", ref.path)
          ref ! GlobalMessages.ReportToRef(reporter)
          ref
        }
      }
    }.getOrElse(
      throw new RuntimeException("An error occurred while trying to connect to the server!")
    )
  }
}

object FrontendService {

  sealed trait FrontendServiceMessages

  object FrontendServiceMessages {
    final case class ExtractSchema(
        con: ConnectionInformation,
        options: ExtractSchemaOptions
    ) extends FrontendServiceMessages

    final case class StopTransformation(ref: ActorSelection) extends FrontendServiceMessages

    final case class AgentsInformations(ref: Option[ActorRef]) extends FrontendServiceMessages

    final case class MappingSuggesterAdvancedSemanticRequest(cookbook: Option[Cookbook])
        extends FrontendServiceMessages

    final case class MappingSuggesterSemanticRequest(cookbook: Option[Cookbook])
        extends FrontendServiceMessages

    final case class MappingSuggesterSimpleRequest(cookbook: Option[Cookbook])
        extends FrontendServiceMessages

    case object Connect extends FrontendServiceMessages

    case object Shutdown extends FrontendServiceMessages

    case object ShutdownServer extends FrontendServiceMessages

    final case class RegisterAgentInformationWebSocket(ref: ActorRef)
        extends FrontendServiceMessages

    final case class AgentsInformationsResponse(agents: Map[String, AgentInformation])
        extends FrontendServiceMessages

    final case class FrontendServiceStatusMessage(message: String) extends FrontendServiceMessages

    final case class ServerConnectionError(message: String) extends FrontendServiceMessages

    /**
      * Ask the service if it has a server connection.
      */
    case object HasServerConnection extends FrontendServiceMessages

    /**
      * The service has a server connection.
      */
    case object ServerConnected extends FrontendServiceMessages

    /**
      * The service has no server connection.
      */
    case object ServerDisconnected extends FrontendServiceMessages
  }

  val name = "FrontendService"

  def props: Props = Props(classOf[FrontendService])

  sealed trait FrontendServiceState

  object FrontendServiceState {

    case object Connecting extends FrontendServiceState

    case object Running extends FrontendServiceState

  }

  /**
    * The state data for the frontend service fsm.
    *
    * @param chef                          An option to the actor ref of the chef de cuisine.
    * @param reportAgentsInformationsTo    A list of actor refs for which we relay agent information updates.
    * @param lastAgentsInformations        The last update from the chef de cuisine.
    * @param lastAgentsInformationsUpdate  The timestamp of the last agents informations update.
    */
  final case class FrontendServiceData(
      chef: Option[ActorRef],
      reportAgentsInformationsTo: List[ActorRef],
      lastAgentsInformations: Map[String, AgentInformation],
      lastAgentsInformationsUpdate: Long
  )

  object FrontendServiceData {

    /**
      * Initialise empty frontend service state data.
      *
      * @return An empty state data for the frontend service FSM.
      */
    def initialise: FrontendServiceData = FrontendServiceData(
      chef = None,
      reportAgentsInformationsTo = List.empty,
      lastAgentsInformations = Map.empty,
      lastAgentsInformationsUpdate = 0L
    )

  }

}
