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

package controllers

import actors.FrontendService
import actors.FrontendService.FrontendServiceMessages
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.Inject
import com.wegtam.tensei.adt.AgentAuthorizationState
import dao.{ AuthDAO, WorkHistoryDAO }
import helpers.CommonTypeAliases.AgentInformationsData
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.UserAuthority
import play.api.{ Configuration, Logger }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.Controller

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

/**
  * This controller is used to display detailed information about connected agents.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param workHistoryDAO The DAO for operation the work history provided via dependency injection.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class AgentsController @Inject()(
    protected val configuration: Configuration,
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    workHistoryDAO: WorkHistoryDAO,
    implicit val system: ActorSystem,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport {
  val log = Logger.logger

  val DEFAULT_ASK_TIMEOUT = 5000L // The fallback default timeout for `ask` operations in milliseconds.

  val frontendSelection = system.actorSelection(s"/user/${FrontendService.name}")
  implicit val timeout = Timeout(
    FiniteDuration(
      configuration.getMilliseconds("tensei.frontend.ask-timeout").getOrElse(DEFAULT_ASK_TIMEOUT),
      MILLISECONDS
    )
  )

  /**
    * A function that returns a `User` object from an `Id`.
    * @todo Currently we override this method from the trait `AuthConfigImpl` because we can't use dependecy injection there to get the appropriate DAO.
    *
    * @param id  The unique database id e.g. primary key for the user.
    * @param ctx The execution context.
    * @return An option to the user wrapped into a future.
    */
  override def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]] =
    authDAO.findAccountById(id)

  /**
    * Request the agent informations from the frontend service and return the results.
    *
    * @return The required informations which may be empty if an error occured.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def fetchAgentInformations: Future[AgentInformationsData] = {
    import play.api.libs.concurrent.Execution.Implicits._

    (frontendSelection ? FrontendServiceMessages.AgentsInformations(None)).map {
      case FrontendServiceMessages.AgentsInformationsResponse(agents) =>
        log.debug("Received agent informations response: {}", agents)
        agents
      case FrontendServiceMessages.ServerConnectionError(message) =>
        log.error("Server connection error: {}", message)
        Map.empty
      case msg =>
        log.error("Received unexpected message while waiting for agent informations. {}", msg)
        Map.empty
    }
  }

  /**
    * Show all connected agents and their reported informations. The uuids of the queue entries that
    * are readable by the user are calculated to be able to hide sensitive information.
    *
    * @return A html page showing the desired information.
    */
  def agents = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          for {
            info <- fetchAgentInformations
            uuids <- workHistoryDAO.filterReadable(
              info.flatMap(_._2.workingState.map(_.uniqueIdentifier.map(e => e))).flatten.toSet,
              uid,
              user.groupIds
            )
          } yield
            Ok(
              views.html.dashboard.agents
                .agentsConnected(info.filter(_._2.auth == AgentAuthorizationState.Connected), uuids)
            )
      }
  }

  /**
    * Display detailed informations about a specific agent.
    *
    * @param agentId The unique ID of the Tensei-Agent.
    * @return A detail page about the agent or an error page.
    */
  def agentDetails(agentId: String) = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id
        .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
          uid =>
            fetchAgentInformations.map(
              info =>
                info
                  .get(agentId)
                  .fold(
                    NotFound(
                      views.html.errors.notFound(Messages("errors.notfound.title"),
                                                 Option(Messages("errors.notfound.header")))
                    )
                  )(
                    agentInfo => Ok(views.html.dashboard.agents.agentDetails(agentInfo))
                )
            )
        }
  }

  /**
    * Show all agents that are disconnected and their reported informations. The uuids of the queue entries that
    * are readable by the user are calculated to be able to hide sensitive information.
    *
    * @return A html page showing the desired information.
    */
  def agentsDisconnected = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          for {
            info <- fetchAgentInformations
            uuids <- workHistoryDAO.filterReadable(
              info.flatMap(_._2.workingState.map(_.uniqueIdentifier.map(e => e))).flatten.toSet,
              uid,
              user.groupIds
            )
          } yield
            Ok(
              views.html.dashboard.agents
                .agentsDisconnected(info.filter(_._2.auth == AgentAuthorizationState.Disconnected),
                                    uuids)
            )
      }
  }

  /**
    * Show all unauthorised agents and their reported informations. The uuids of the queue entries that
    * are readable by the user are calculated to be able to hide sensitive information.
    *
    * @return A html page showing the desired information.
    */
  def agentsUnauthorized = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          for {
            info <- fetchAgentInformations
            uuids <- workHistoryDAO.filterReadable(
              info.flatMap(_._2.workingState.map(_.uniqueIdentifier.map(e => e))).flatten.toSet,
              uid,
              user.groupIds
            )
          } yield
            Ok(
              views.html.dashboard.agents
                .agentsUnauthorized(info.filter(_._2.auth == AgentAuthorizationState.Unauthorized),
                                    uuids)
            )
      }
  }
}
