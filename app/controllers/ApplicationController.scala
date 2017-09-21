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

import javax.inject.Inject

import actors.FrontendService
import actors.FrontendService.FrontendServiceMessages
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.wegtam.tensei.adt.TenseiLicenseMessages
import dao.AuthDAO
import forms.UnlockForm
import jp.t2v.lab.play2.auth.OptionalAuthElement
import models.Account
import models.Authorities.UserAuthority
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsBoolean, JsValue}
import play.api.mvc._
import play.api.routing._
import play.api.{Configuration, Logger}
import play.filters.csrf.CSRF

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * The main entry point for the application.
  * It provides the start page and login and logout actions.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class ApplicationController @Inject() (
    protected val configuration: Configuration,
    val messagesApi:             MessagesApi,
    authDAO:                     AuthDAO,
    implicit val system:         ActorSystem,
    implicit val webJarAssets:   WebJarAssets
) extends Controller with I18nSupport with OptionalAuthElement with AuthConfigImpl {

  private val log = Logger.logger

  val DEFAULT_ASK_TIMEOUT = 5000L // The fallback default timeout for `ask` operations in milliseconds.
  private val frontendSelection = system.actorSelection(s"/user/${FrontendService.name}")
  implicit val timeout: Timeout = Timeout(FiniteDuration(configuration.getMilliseconds("tensei.frontend.ask-timeout").getOrElse(DEFAULT_ASK_TIMEOUT), MILLISECONDS))

  /**
    * A function that returns a `User` object from an `Id`.
    * You can alter the procedure to suit your application.
    *
    * @return A future holding an option to the resolved user.
    */
  override def resolveUser(id: Int)(implicit context: ExecutionContext): Future[Option[Account]] = authDAO.findAccountById(id)

  /**
    * Query the frontend service to check for the presence of a license (license meta data).
    *
    * @return Either true or false.
    */
  def checkLicensePresence: Action[AnyContent] = AsyncStack() {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val user: Option[Account] = loggedIn

      if (user.isEmpty)
        Future.successful(Forbidden(""))
      else {
        val metaData: Future[TenseiLicenseMessages] = frontendSelection.ask(TenseiLicenseMessages.ReportLicenseMetaData).mapTo[TenseiLicenseMessages].recover {
          case _ ⇒ TenseiLicenseMessages.NoLicenseInstalled
        }
        val presence: Future[Boolean] = metaData.map {
          case TenseiLicenseMessages.LicenseMetaData(_, _, _) ⇒ true
          case _ ⇒ false
        }
        presence.map {
          p ⇒
            render {
              case Accepts.Html() ⇒ Ok(p.toString)
              case Accepts.Json() ⇒ Ok(JsBoolean(p): JsValue)
            }
        }
      }

  }
  /**
    * Query the frontend service to check for a valid connection to the Tensei server component.
    *
    * @return Either true or false.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def checkServerConnection: Action[AnyContent] = Action.async {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val status: Future[Boolean] = (frontendSelection ? FrontendServiceMessages.HasServerConnection).map {
        case FrontendServiceMessages.ServerConnected    ⇒ true
        case FrontendServiceMessages.ServerDisconnected ⇒ false
        case msg ⇒
          log.error("Received unexpected message while waiting for server connection status! {}", msg)
          false
      }
      status.map(
        s ⇒ render {
          case Accepts.Html() ⇒ Ok(s.toString)
          case Accepts.Json() ⇒ Ok(JsBoolean(s): JsValue)
        }
      )
  }

  /**
    * The main entry point for the application.
    *
    * @return Either a redirect to the login, the dashboard or the setup form.
    */
  def index: Action[AnyContent] = AsyncStack() {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      authDAO.hasAdminAccount.map(
        hasAdmin ⇒
          if (hasAdmin)
            loggedIn.fold(Redirect(routes.Authentication.login()))(_ ⇒ Redirect(routes.DashboardController.index()))
          else
            Redirect(routes.Authentication.setup())
      )
  }

  /**
    * Provide reverse routing information for javascript functions.
    *
    * @return Either the routes for javascript or a forbidden error.
    */
  def javascriptRoutes: Action[AnyContent] = AsyncStack() {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      loggedIn.fold(Future.successful(Forbidden("You are not authorised to access this resource!")))(
        user ⇒ authorize(user, UserAuthority).map(
          can ⇒
            if (can)
              Ok(JavaScriptReverseRouter("jsRoutes")(
              routes.javascript.ApplicationController.checkLicensePresence,
              routes.javascript.ApplicationController.checkServerConnection,
              routes.javascript.ApplicationController.index,
              routes.javascript.AgentRunLogsController.webSocket,
              routes.javascript.ConnectionInformationsController.autoComplete,
              routes.javascript.ConnectionInformationsController.index,
              routes.javascript.CookbookResourcesController.show,
              routes.javascript.CookbookResourcesController.suggestMappings,
              routes.javascript.DashboardController.enqueue,
              routes.javascript.DashboardController.stopWorkingQueueEntry,
              routes.javascript.DashboardController.webSocket,
              routes.javascript.DFASDLResourcesController.getIdFromName,
              routes.javascript.DFASDLResourcesController.validateDFASDL,
              routes.javascript.TransformationConfigurationsController.show,
              routes.javascript.TransformationConfigurationsController.generateFormData
            )).as("text/javascript")
            else
              Forbidden("You are not authorised to access this resource!")
        )
      )
  }

  /**
    * Display the error page for a missing server connection.
    *
    * @return The server connection error page.
    */
  def serverConnectionMissing = Action {
    implicit request ⇒
      Ok(views.html.errors.serverConnection())
  }

  /**
    * Provide a way to reset the user's password if the unlock token is valid and re-enable the
    * account.
    *
    * @param unlockToken An unlock token.
    * @return Either an error page or a html form.
    */
  def unlock(unlockToken: String): Action[AnyContent] = AsyncStack() {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden())))(
        implicit token ⇒
          authDAO.findAccountByUnlockToken(unlockToken).map(
            ao ⇒
              if (unlockToken.length != Account.UNLOCK_TOKEN_LENGTH || ao.isEmpty)
                NotFound(views.html.errors.notFound(Messages("unlock.error.not-found.title"), Option(Messages("unlock.error.not-found.message"))))
              else {
                val canAccessUnlockPage: Boolean = loggedIn.fold(true)(a ⇒ a.isAdmin) // Either not logged in or logged in as admin suffices for access.
                if (canAccessUnlockPage)
                  Ok(views.html.unlock(UnlockForm.form.fill(UnlockForm.Data(password = ("", ""), unlockToken = unlockToken))))
                else
                  Forbidden(views.html.errors.forbidden())
              }
          )
      )
  }
}
