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

import dao.AuthDAO
import forms.{ LoginForm, SetupForm, UnlockForm }
import jp.t2v.lab.play2.auth.LoginLogout
import models.Account
import org.slf4j
import play.api.{ Configuration, Logger }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.{ Action, AnyContent, Controller }
import play.filters.csrf.CSRF

import scala.concurrent.{ ExecutionContext, Future }

/**
  * The controller handles authentication related tasks like use login, logut and the
  * initial setup of the administrator account.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class Authentication @Inject()(
    protected val configuration: Configuration,
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with I18nSupport
    with LoginLogout
    with AuthConfigImpl {

  val log: slf4j.Logger = Logger.logger

  /**
    * A function that returns a `User` object from an `Id`.
    * You can alter the procedure to suit your application.
    *
    * @return A future holding an option to the resolved user.
    */
  override def resolveUser(id: Int)(implicit context: ExecutionContext): Future[Option[Account]] =
    authDAO.findAccountById(id)

  /**
    * Process the login form and try to authenticate the user against the database.
    *
    * @return A future holding either a redirect or an error.
    */
  def authenticate: Action[AnyContent] = Action.async { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden())))(
      implicit token =>
        LoginForm.form
          .bindFromRequest()
          .fold(
            formWithErrors => Future.successful(BadRequest(views.html.login(formWithErrors))),
            formData => {
              val auth = authDAO.authenticate(formData.email, formData.password)
              for {
                ar <- auth
                result <- ar.fold(
                  authDAO
                    .incrementFailedLoginAttempts(formData.email)
                    .map(
                      _ =>
                        BadRequest(
                          views.html.login(
                            LoginForm.form
                              .fill(formData)
                              .withGlobalError(Messages("errors.signin.auth-failure"))
                          )
                      )
                    )
                )(
                  a =>
                    a.id.fold(
                      Future.successful(
                        InternalServerError(views.html.errors.internalServerError(None))
                      )
                    )(id => gotoLoginSucceeded(id))
                )
              } yield result
            }
        )
    )
  }

  /**
    * Try to create an administrator account within the database.
    *
    * @return Redirect to the start page or return a bad request.
    */
  def createAdminAccount: Action[AnyContent] = Action.async { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden())))(
      implicit token =>
        SetupForm.form
          .bindFromRequest()
          .fold(
            formWithErrors => Future.successful(BadRequest(views.html.setup(formWithErrors))),
            formData =>
              authDAO.hasAdminAccount.flatMap(
                hasAdmin =>
                  if (hasAdmin)
                    Future.successful(
                      Redirect(routes.ApplicationController.index())
                        .flashing("error" -> Messages("setup.error.has-admin"))
                    )
                  else {
                    val a = Account
                      .generateGenericAccount(email = formData.email,
                                              password = formData.password._1)
                      .copy(isAdmin = true)
                    for {
                      c <- authDAO.createAccount(a)
                      r <- c match {
                        case scala.util.Failure(e) =>
                          log.error("Could not create admin account!", e)
                          Future.successful(
                            BadRequest(
                              views.html.setup(
                                SetupForm.form
                                  .fill(formData)
                                  .withGlobalError("Could not create admin account!")
                              )
                            )
                          )
                        case scala.util.Success(ac) =>
                          ac.id.fold(
                            Future.successful(
                              BadRequest(
                                views.html.setup(
                                  SetupForm.form
                                    .fill(formData)
                                    .withGlobalError("Could not create admin account!")
                                )
                              )
                            )
                          )(id => gotoLoginSucceeded(id))
                      }
                    } yield r
                }
            )
        )
    )
  }

  /**
    * Display the login form.
    *
    * @return The login form.
    */
  def login: Action[AnyContent] = Action.async { implicit request =>
    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden())))(
      implicit token => Future.successful(Ok(views.html.login(LoginForm.form)))
    )
  }

  /**
    * Log the user out of the application.
    *
    * @return The result of `AuthConfigImpl.gotoLogoutSucceeded` with a flashing message.
    */
  def logout: Action[AnyContent] = Action.async { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden())))(
      implicit token =>
        gotoLogoutSucceeded.map(_.flashing("success" -> Messages("auth.logout.success")))
    )
  }

  /**
    * Display the setup form if no administrator accounts are defined.
    *
    * @return Either the html form or a redirect with a flashing error message.
    */
  def setup: Action[AnyContent] = Action.async { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden())))(
      implicit token =>
        authDAO.hasAdminAccount.map(
          hasAdmin =>
            if (hasAdmin)
              Redirect(routes.ApplicationController.index())
                .flashing("error" -> Messages("setup.error.has-admin"))
            else
              Ok(views.html.setup(SetupForm.form))
      )
    )
  }

  /**
    * Process the unlock form.
    *
    * @return Either redirect the user to the login page or give a bad request if an error occured.
    */
  def unlockUser: Action[AnyContent] = Action.async { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden())))(
      implicit token =>
        UnlockForm.form
          .bindFromRequest()
          .fold(
            formWithErrors => Future.successful(BadRequest(views.html.unlock(formWithErrors))),
            formData =>
              authDAO
                .findAccountByUnlockToken(formData.unlockToken)
                .flatMap(
                  ao =>
                    ao.fold(
                      Future.successful(
                        NotFound(
                          views.html.errors.notFound(Messages("errors.notfound.title"), None)
                        )
                      )
                    )(
                      a =>
                        authDAO
                          .unlockAccountAndSetPassword(a.copy(password = formData.password._1))
                          .map(
                            _ =>
                              Redirect(routes.Authentication.login())
                                .flashing("success" -> Messages("unlock.success"))
                        )
                  )
              )
        )
    )
  }

}
