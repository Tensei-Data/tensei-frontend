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

import javax.inject.{ Inject, Named }

import actors.websockets.AgentRunLogUpdate
import actors.websockets.AgentRunLogUpdate.UpdateSocketOptions
import actors.{ AgentRunLogsFetcher, FrontendService }
import akka.actor.{ ActorRef, ActorSystem }
import akka.stream.Materializer
import dao._
import jp.t2v.lab.play2.auth.AuthElement
import models.{ Account, TransformationConfiguration }
import models.Authorities.UserAuthority
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.{ Controller, WebSocket }
import play.api.libs.json._
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer

import scala.concurrent.{ ExecutionContext, Future }

/**
  * The controller manages the display of log messages from agent runs.
  *
  * @param fetcher The actor ref of the actor that will fetch the run logs in the background provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param agentRunLogsDAO The DAO for operating buffered agent run logs.
  * @param workHistoryDAO The DAO for operating the work history.
  * @param tcDAO The DAO for handling transformation configurations.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param materializer An akka streams materialiser provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class AgentRunLogsController @Inject()(
    @Named("AgentRunLogsFetcher") fetcher: ActorRef,
    val messagesApi: MessagesApi,
    val authDAO: AuthDAO,
    val agentRunLogsDAO: AgentRunLogsDAO,
    val workHistoryDAO: WorkHistoryDAO,
    val tcDAO: TransformationConfigurationDAO,
    implicit val system: ActorSystem,
    implicit val materializer: Materializer,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport {
  val frontendSelection = system.actorSelection(s"/user/${FrontendService.name}")

  /**
    * A function that returns a `User` object from an `Id`.
    * You can alter the procedure to suit your application.
    */
  override def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[Account]] =
    authDAO.findAccountById(id)

  /**
    * If a transformation configuration run with the given id exists then
    * the stored log files are shown.
    *
    * @param uuid The id of a transformation configuration run.
    * @return Either the logs associated with the run or a not found page.
    */
  def show(uuid: String) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    for {
      ho <- workHistoryDAO.findById(uuid)
      to <- ho.fold(Future.successful(None: Option[TransformationConfiguration]))(
        h => tcDAO.findById(h.tkid)
      )
      auth      <- to.fold(Future.successful(false))(t => authorize(user, t.getReadAuthorisation))
      ls        <- agentRunLogsDAO.all(uuid) if auth && to.isDefined
      maxOffset <- agentRunLogsDAO.getMaxOffset(uuid) if auth && to.isDefined
      startedBy <- ho.fold(Future.successful(None: Option[Account]))(
        h =>
          h.user.fold(Future.successful(None: Option[Account]))(id => authDAO.findAccountById(id))
      ) if auth
    } yield {
      ho.fold(
        NotFound(
          views.html.errors.notFound(Messages("errors.notfound.title"),
                                     Option(Messages("errors.notfound.header")))
        )
      )(
        historyEntry =>
          to.fold(
            NotFound(
              views.html.errors.notFound(Messages("errors.notfound.title"),
                                         Option(Messages("errors.notfound.header")))
            )
          ) { tc =>
            if (auth) {
              fetcher ! AgentRunLogsFetcher.FetchLogs(uuid, maxOffset)
              Ok(
                views.html.dashboard.agentRunLogs
                  .show(tc.name, startedBy.map(_.email), historyEntry, ls)
              )
            } else
              Forbidden(views.html.errors.forbidden())
        }
      )
    }
  }

  implicit val webSocketFrameFormatter =
    MessageFlowTransformer.jsonMessageFlowTransformer[UpdateSocketOptions, JsValue]

  /**
    * Create a websocket that will fetch new log lines from the database
    * upon request.
    *
    * @todo Protect with authorisation.
    * @return A websocket.
    */
  def webSocket = WebSocket.accept[UpdateSocketOptions, JsValue] { request =>
    ActorFlow.actorRef(out => AgentRunLogUpdate.props(out, agentRunLogsDAO, fetcher))
  }

}
