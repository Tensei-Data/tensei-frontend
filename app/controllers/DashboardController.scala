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

import java.time.LocalDateTime

import actors.FrontendService.FrontendServiceMessages
import actors.WorkQueueMaster.WorkQueueMasterMessages
import actors.websockets.DashboardWebsocket
import actors.{ FrontendService, WorkQueueMaster }
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.google.inject.Inject
import dao._
import helpers.CommonTypeAliases._
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.UserAuthority
import models._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ JsObject, JsString, JsValue }
import play.api.libs.streams.ActorFlow
import play.api.mvc.{ Controller, WebSocket }
import play.api.{ Configuration, Logger }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

/**
  * The dashboard controller provides all kinds of functionalities to make the dashboard
  * operational.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param connectionInformationResourceDAO The DAO for handling connection informations provided via dependency injection.
  * @param cronDAO The DAO for managing crontab entries provided via dependency injection.
  * @param triggerDAO The DAO for managing triggers provided via dependency injection.
  * @param workQueueDAO The DAO for managing the work queue provided via dependency injection.
  * @param transformationConfigurationDAO The DAO for managing transformation configurations provided via dependency injection.
  * @param workHistoryDAO The DAO for managing the work history provided via dependency injection.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param materializer An akka streams materialiser provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class DashboardController @Inject()(
    protected val configuration: Configuration,
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    connectionInformationResourceDAO: ConnectionInformationResourceDAO,
    cronDAO: CronDAO,
    triggerDAO: TriggerDAO,
    workQueueDAO: WorkQueueDAO,
    transformationConfigurationDAO: TransformationConfigurationDAO,
    workHistoryDAO: WorkHistoryDAO,
    implicit val system: ActorSystem,
    implicit val materializer: Materializer,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport {
  val log = Logger.logger

  val DEFAULT_DB_TIMEOUT    = 10000L  // The fallback default timeout for database operations in milliseconds.
  val DEFAULT_ASK_TIMEOUT   = 5000L   // The fallback default timeout for `ask` operations in milliseconds.
  val DEFAULT_STATS_TIMEOUT = 300000L // The fallback default timeout for generating statistics from the history queue in milliseconds.

  val frontendSelection = system.actorSelection(s"/user/${FrontendService.name}")
  implicit val timeout = Timeout(
    FiniteDuration(
      configuration.getMilliseconds("tensei.frontend.ask-timeout").getOrElse(DEFAULT_ASK_TIMEOUT),
      MILLISECONDS
    )
  )
  lazy val dbTimeout = FiniteDuration(
    configuration.getMilliseconds("tensei.frontend.db-timeout").getOrElse(DEFAULT_DB_TIMEOUT),
    MILLISECONDS
  )
  lazy val statsTimeout = FiniteDuration(
    configuration
      .getMilliseconds("tensei.frontend.ui.statistics.history-timeout")
      .getOrElse(DEFAULT_STATS_TIMEOUT),
    MILLISECONDS
  )

  val tcQueueMaster = system.actorSelection(s"/user/${WorkQueueMaster.name}")

  /**
    * A function that returns a `User` object from an `Id`.
    *
    * @todo Currently we override this method from the trait `AuthConfigImpl` because we can't use dependecy injection there to get the appropriate DAO.
    * @param id  The unique database id e.g. primary key for the user.
    * @param context The execution context.
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
    * Display the main dashboard for the user.
    *
    * @return The main dashboard or an error page.
    */
  def index = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    log.info("USER: {}", user)
    if (user.watchedIntro) {
      user.id.fold(
        Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
      ) { uid =>
        val transformationConfigurations =
          transformationConfigurationDAO.allReadable(uid, user.groupIds)
        val connectionInformations =
          connectionInformationResourceDAO.allReadable(uid, user.groupIds)
        val cronjobs       = cronDAO.allReadable(uid, user.groupIds)
        val triggers       = triggerDAO.allReadable(uid, user.groupIds)
        val tcQueueEntries = workQueueDAO.allReadable(uid, user.groupIds)
        val stats          = workHistoryDAO.calculateStatistics(uid, user.groupIds)
        for {
          tc  <- transformationConfigurations
          as  <- fetchAgentInformations.recover { case ex => Map.empty: AgentInformationsData }
          cis <- connectionInformations
          cs  <- cronjobs
          ts  <- triggers
          qs  <- tcQueueEntries
          ss  <- stats
          tss <- WorkHistoryStatistics.translateStats(ss)
        } yield {
          Ok(views.html.dashboard.index(loggedIn.email, tc, as, cis, cs, ts, qs, tss))
        }
      }
    } else
      authDAO.watchedIntro(user).map(_ => Redirect(routes.HelpController.intro()))
  }

  /**
    * Put the transformation configuration with the given id into the work queue.
    *
    * @param id The database ID of a transformation configuration.
    * @return
    */
  def enqueue(id: Long) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          for {
            to <- transformationConfigurationDAO.findById(id)
            auth <- to
              .fold(Future.successful(false))(t => authorize(user, t.getExecuteAuthorisation))
          } yield {
            render {
              case Accepts.Html() => UnsupportedMediaType("Please use application/json.")
              case Accepts.Json() =>
                to.fold(NotFound(JsObject(List("status" -> JsString("404 - Not Found")))))(
                  t =>
                    if (auth) {
                      val e = WorkQueueEntry.fromUser(id, uid)
                      tcQueueMaster ! WorkQueueMasterMessages.AddToQueue(e)
                      Ok(
                        JsObject(
                          List(
                            "status" -> JsString(
                              s"Transformation configuration $id was sent to queue."
                            )
                          )
                        )
                      )
                    } else
                      Forbidden(JsObject(List("status" -> JsString("403 - Forbidden"))))
                )
            }
          }
      }
  }

  /**
    * Try to abort the currently running queue entry with the given id.
    *
    * @param uuid The ID of the history entry.
    * @return The appropriate http status and a message.
    */
  def stopWorkingQueueEntry(uuid: String) = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(
        Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
      ) { uid =>
        for {
          ho     <- workHistoryDAO.findRunningById(uuid)
          uuids  <- workHistoryDAO.filterReadable(Set(uuid), uid, user.groupIds)
          agents <- fetchAgentInformations if ho.isDefined && uuids.contains(uuid)
          result <- agents
            .find(
              _._2.workingState.fold(false)(
                w =>
                  w.uniqueIdentifier.fold(false)(
                    u => u == uuid
                )
              )
            )
            .fold(
              ho.fold(Future.successful(0))(
                h =>
                  workHistoryDAO.update(
                    h.copy(finished = Option(java.sql.Timestamp.valueOf(LocalDateTime.now())),
                           failed = true)
                )
              )
            ) { agentInfo =>
              val agentSel = system.actorSelection(agentInfo._2.path)
              frontendSelection ! FrontendServiceMessages.StopTransformation(agentSel)
              ho.fold(Future.successful(0))(
                h =>
                  workHistoryDAO.update(
                    h.copy(finished = Option(java.sql.Timestamp.valueOf(LocalDateTime.now())),
                           aborted = true)
                )
              )
            } if ho.isDefined && uuids.contains(uuid)
        } yield {
          render {
            case Accepts.Html() => UnsupportedMediaType("Please use application/json.")
            case Accepts.Json() =>
              ho.fold(NotFound(JsObject(List("status" -> JsString("404 - Not Found"))))) { t =>
                if (uuids.contains(uuid)) {
                  if (result > 0)
                    Ok(
                      JsObject(
                        List(
                          "status" -> JsString(s"Abort command for job $uuid was sent to agent.")
                        )
                      )
                    )
                  else
                    NotFound(JsObject(List("status" -> JsString("404 - Not Found"))))
                } else
                  Forbidden(JsObject(List("status" -> JsString("403 - Forbidden"))))
              }
          }
        }
      }
  }

  /**
    * Create a websocket that will be used to dynamically update informations
    * on the dashboard and relay commands that are passed via ajax.
    *
    * @todo Protect with authorisation.
    * @return A websocket.
    */
  def webSocket = WebSocket.accept[JsValue, JsValue] { request =>
    ActorFlow.actorRef(
      out =>
        DashboardWebsocket.props(configuration,
                                 workQueueDAO,
                                 workHistoryDAO,
                                 out,
                                 request2Messages(request),
                                 webJarAssets)
    )
  }
}
