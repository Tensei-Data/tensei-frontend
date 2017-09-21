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

package actors.websockets

import java.sql.Timestamp
import java.time.format.DateTimeFormatter

import argonaut._
import Argonaut._
import actors.FrontendService
import actors.FrontendService.FrontendServiceMessages
import actors.websockets.DashboardWebsocket.DashboardWebsocketMessages
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.pattern.ask
import akka.util.Timeout
import com.wegtam.tensei.adt.{ AgentInformation, AgentWorkingState }
import com.wegtam.tensei.agent.{ ParserState, ProcessorState }
import controllers.WebJarAssets
import dao.{ WorkHistoryDAO, WorkQueueDAO }
import helpers.CommonTypeAliases.AgentInformationsData
import models.{ MemoryStats, SystemLoadStats, WorkHistoryEntry }
import play.api.Configuration
import play.api.i18n.Messages
import play.api.mvc.Controller
import play.twirl.api.Html
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * This websocket provides functionalities and interactions that are needed on the dashboard.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param workQueueDAO The DAO for handling the transformation configuration queue provided via dependency injection.
  * @param workHistoryDAO The DAO for handling the transformation configuration history provided via dependency injection.
  * @param out The actor ref to the actor that communicates with the browser websocket.
  * @param messages Provides messages for translation and internationalisation provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class DashboardWebsocket(
    protected val configuration: Configuration,
    workQueueDAO: WorkQueueDAO,
    workHistoryDAO: WorkHistoryDAO,
    out: ActorRef,
    messages: Messages,
    webJarAssets: WebJarAssets
) extends Actor
    with ActorLogging
    with Controller {
  import context.dispatcher

  val DEFAULT_TIMEOUT        = 5000L // The fallback default timeout in milliseconds.
  val DEFAULT_AGENT_INTERVAL = 3000L // The fallback default interval for polling agent informations in milliseconds.
  val DEFAULT_QUEUE_INTERVAL = 1000L // The fallback default interval for polling the transformation queue in milliseconds.

  private val frontendSelection = context.system.actorSelection(s"/user/${FrontendService.name}")
  private val infoPollingInterval = FiniteDuration(
    configuration
      .getMilliseconds("tensei.frontend.agent-information-polling-interval")
      .getOrElse(DEFAULT_AGENT_INTERVAL),
    MILLISECONDS
  )

  private val agentInformationScheduler = context.system.scheduler.schedule(
    FiniteDuration(50, MILLISECONDS),
    infoPollingInterval,
    self,
    DashboardWebsocketMessages.PollAgentsInformations
  )

  type TCQueueHistoryType = (String,
                             Long,
                             Timestamp,
                             Option[Timestamp],
                             Option[Timestamp],
                             Boolean,
                             Boolean,
                             Long,
                             Boolean,
                             Boolean,
                             Boolean,
                             Option[String])

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    log.debug("Starting frontend websocket actor at '{}'.", self.path)
    super.preStart()
  }

  override def postStop(): Unit = {
    log.debug("Stopping frontend websocket actor.")
    val _ = agentInformationScheduler.cancel()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.NonUnitStatements"))
  def receive: Receive = {
    case DashboardWebsocketMessages.PollAgentsInformations =>
      log.debug("Polling for agents informations.")
      loadAgentsInformations.foreach(info => createOutputInformation(info)(messages, webJarAssets))
    case DashboardWebsocketMessages.PollQueueInformations =>
      workQueueDAO.all.foreach { ts =>
        val msg: JsValue = JsObject(
          Seq(
            "type"    -> JsString("queueInfo"),
            "message" -> JsString(ts.toList.asJson.nospaces)
          )
        )
        out ! msg
      }
    case message: FrontendServiceMessages.AgentsInformationsResponse =>
      log.debug("Got pushed agents informations repsonse.")
      outputInformations(out, message)
  }

  /**
    * Create the output for the transformation configurations and agents to send it to the dashboard.
    *
    * @param info The agent informations.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Product", "org.wartremover.warts.Serializable"))
  private def createOutputInformation(
      info: AgentInformationsData
  )(implicit messages: Messages, webJarAssets: WebJarAssets): Future[Unit] =
    for {
      activeEntries   <- workHistoryDAO.allActive
      finishedEntries <- workHistoryDAO.lastFinished(None)
    } yield {
      val htmlTKIDs  = createTKEntries(activeEntries, finishedEntries, info)
      val htmlAgents = createAgentEntries(activeEntries, info)
      val msg: JsValue = JsObject(
        Seq(
          "type" -> JsString("dashboard"),
          "message" -> JsObject(
            Seq(
              "tkentries" -> JsObject(
                htmlTKIDs.toSeq.map(t => t._1.toString -> JsString(t._2))
              ),
              "agents" -> JsObject(
                htmlAgents.toSeq.map(a => a._1 -> JsString(a._2))
              )
            )
          )
        )
      )
      out ! msg
    }

  /**
    * Create the HTML for the transformation configurations
    *
    * @param activeEntries     The currently not finished transformations
    * @param finishedEntries   Up to 2 finished transformations per transformation configuration
    * @param info            The actual information by the agents
    */
  def createTKEntries(
      activeEntries: Seq[WorkHistoryEntry],
      finishedEntries: Seq[WorkHistoryEntry],
      info: AgentInformationsData
  )(implicit messages: Messages, webJarAssets: WebJarAssets): Map[Long, String] = {
    log.debug("{} active and {} finished queue entries.", activeEntries.size, finishedEntries.size)
    val tcIds = activeEntries.map(_.tkid).toSet ++ finishedEntries.map(_.tkid).toSet
    val html = tcIds.toList.map { id =>
      (id,
       createActiveTKEntries(id, activeEntries, info) + createFinishedTKEntries(id,
                                                                                finishedEntries))
    }
    html.toMap
  }

  /**
    * Create the HTML for the currently active agents
    *
    * @param infos The information of the current agents
    */
  def createAgentEntries(
      activeEntries: Seq[WorkHistoryEntry],
      infos: AgentInformationsData
  )(implicit messages: Messages, webJarAssets: WebJarAssets): Map[String, String] =
    infos.map { i =>
      val id: Option[Long] = i._2.workingState.fold(None: Option[Long])(
        _.uniqueIdentifier.fold(None: Option[Long])(
          uuid =>
            activeEntries.find(e => e.uuid == uuid).fold(None: Option[Long])(e => Option(e.tkid))
        )
      )
      (i._1, createAgentHTML(id, i))
    }

  /**
    * Create the HTML for one agent
    *
    * @param agent The information about the agent
    * @return The HTML for the specific agent
    */
  def createAgentHTML(
      tkid: Option[Long],
      agent: (String, AgentInformation)
  )(implicit messages: Messages, webJarAssets: WebJarAssets): String = {
    val totalRam =
      agent._2.workingState.map(s => s.runtimeStats.map(r => r._2.totalMemory).fold(0L)(_ + _))
    views.html.helpers.dashboard
      .agent(agent._1, agent._2, tkid, createAgentNodeIndicators(agent._2.workingState), totalRam)
      .toString()
  }

  /**
    * Create the HTML for the agent nodes.
    *
    * @param workingState The agent's working state.
    * @return The HTML for the node representations.
    */
  def createAgentNodeIndicators(
      workingState: Option[AgentWorkingState]
  )(implicit messages: Messages, webJarAssets: WebJarAssets): Option[Iterable[Html]] =
    workingState.fold[Option[Iterable[Html]]](None) { workingState =>
      val runtimeStats = workingState.runtimeStats
      val nodeIndicators =
        runtimeStats.map { entry =>
          val nodeName  = entry._1
          val nodeStats = entry._2

          val loadStats = SystemLoadStats(
            load = nodeStats.systemLoad.getOrElse(0),
            maxLoad = 10,
            processors = nodeStats.processors
          )
          val memoryStats = MemoryStats(
            total = nodeStats.totalMemory,
            free = nodeStats.freeMemory
          )

          val tooltipText =
            views.html.helpers.dashboard.agentNodeTooltip(memoryStats, loadStats).toString()

          views.html.helpers.dashboard
            .agentNodeIndicator(nodeName, memoryStats, loadStats, tooltipText)
        }
      Option(nodeIndicators)
    }

  /**
    * Create the HTML for an active transformation configuration
    *
    * @param tkid                  The transformation configuration ID
    * @param activeTransformations Active transformations for the specific ID
    * @param infos            The current agent information
    * @return A String with the HTML
    */
  def createActiveTKEntries(
      tkid: Long,
      activeTransformations: Seq[WorkHistoryEntry],
      infos: AgentInformationsData
  )(implicit messages: Messages, webJarAssets: WebJarAssets): String = {
    val html = activeTransformations.flatMap(
      h =>
        if (tkid == h.tkid) {
          val uuid = h.uuid
          val agent =
            infos.filter(_._2.workingState.fold(false)(_.uniqueIdentifier.contains(uuid)))
          val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", messages.lang.toLocale)
          val started = h.started
            .fold(h.created.toLocalDateTime.format(format))(_.toLocalDateTime.format(format))
          val runtime = h.started.fold(java.time.Duration.ZERO)(
            s =>
              h.finished.fold(
                java.time.Duration.between(s.toInstant, java.time.ZonedDateTime.now().toInstant)
              )(f => java.time.Duration.between(s.toInstant, f.toInstant))
          )
          val percentage =
            agent.headOption.map(a => calculatePercentageCompleted(a._2.workingState))
          Option(
            views.html.helpers.dashboard
              .activeTransformationConfiguration(h.cron,
                                                 h.trigger,
                                                 h.user,
                                                 started,
                                                 agent,
                                                 percentage,
                                                 runtime,
                                                 tkid,
                                                 uuid)
              .toString()
          )
        } else
        None
    )
    html.mkString
  }

  /**
    * Helper function that calculates the percentage level for the progress bar.
    *
    * @param workingState The working state of the agent
    * @return The level as Long
    */
  def calculatePercentageCompleted(workingState: Option[AgentWorkingState]): Int =
    workingState.fold(0) { ws =>
      val parser: Int = ws.parser match {
        case ParserState.ValidatingSyntax       => 5
        case ParserState.ValidatingAccess       => 7
        case ParserState.ValidatingChecksums    => 10
        case ParserState.InitializingSubParsers => 15
        case ParserState.Parsing                => 30
        case _                                  => 0
      }
      val processor: Int = ws.processor match {
        case ProcessorState.Sorting                 => 35
        case ProcessorState.Processing              => 65
        case ProcessorState.WaitingForWriterClosing => 95
        case _                                      => 0
      }

      if (parser > 0)
        parser
      else
        processor
    }

  /**
    * Create the HTML for a finished transformation configuration
    *
    * @param tkid                    The ID of the transformation configuration
    * @param finishedTransformations Finished transformation configurations for this ID
    * @return The HTML of the finished transformations
    */
  def createFinishedTKEntries(
      tkid: Long,
      finishedTransformations: Seq[WorkHistoryEntry]
  )(implicit messages: Messages, webJarAssets: WebJarAssets): String = {
    // FIXME The `take(x)` can be removed if the Slick `distinctOn` works on the query!
    val html = finishedTransformations.filter(_.tkid == tkid).take(1).map { h =>
      val uuid     = h.uuid
      val format   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", messages.lang.toLocale)
      val finished = h.finished.fold("-")(_.toLocalDateTime.format(format))
      val runtime = h.started.fold(java.time.Duration.ZERO)(
        s =>
          h.finished.fold(java.time.Duration.ZERO)(
            f => java.time.Duration.between(s.toInstant, f.toInstant)
        )
      )
      views.html.helpers.dashboard.finishedTransformationConfiguration(uuid,
                                                                       h.cron,
                                                                       h.trigger,
                                                                       h.user,
                                                                       finished,
                                                                       runtime,
                                                                       h.completed,
                                                                       h.aborted,
                                                                       h.failed,
                                                                       h.message)
    }
    html.mkString
  }

  /**
    * Try to load the agent informations from the frontend service actor.
    * If an error occures an empty information map is returned.
    *
    * @return A future holding the agent informations.
    */
  private def loadAgentsInformations: Future[AgentInformationsData] = {
    implicit val timeout: Timeout = Timeout(DEFAULT_TIMEOUT, MILLISECONDS)
    val getAgentInfo = (frontendSelection ? FrontendServiceMessages.AgentsInformations(None))
      .mapTo[FrontendServiceMessages]
    getAgentInfo.map {
      case FrontendServiceMessages.AgentsInformationsResponse(agents) =>
        log.debug("Received agent informations. {}", agents)
        agents
      case FrontendServiceMessages.ServerConnectionError(message) =>
        log.error("Server connection error! {}", message)
        Map.empty[String, AgentInformation]
      case msg =>
        log.error("Received unexpected message while waiting for agent informations! {}", msg)
        Map.empty[String, AgentInformation]
    }
  }

  /**
    * Send the given agents informations to an actor ref as useable websocket outpout.
    *
    * @param recipient The actor ref of the recipient (web socket).
    * @param message   The agents informations response from the frontend service.
    */
  private def outputInformations(
      recipient: ActorRef,
      message: FrontendServiceMessages.AgentsInformationsResponse
  ): Unit = {
    val msg: JsValue = JsObject(
      Seq(
        "type"    -> JsString("agentInfo"),
        "message" -> JsString(message.agents.asJson.nospaces)
      )
    )
    recipient ! msg
  }
}

object DashboardWebsocket {
  def props(configuration: Configuration,
            tcQueueDAO: WorkQueueDAO,
            tcQueueHistoryDAO: WorkHistoryDAO,
            output: ActorRef,
            messages: Messages,
            webJarAssets: WebJarAssets) =
    Props(
      new DashboardWebsocket(configuration,
                             tcQueueDAO,
                             tcQueueHistoryDAO,
                             output,
                             messages,
                             webJarAssets)
    )

  sealed trait DashboardWebsocketMessages

  object DashboardWebsocketMessages {

    case object PollAgentsInformations extends DashboardWebsocketMessages

    case object PollQueueInformations extends DashboardWebsocketMessages

  }
}
