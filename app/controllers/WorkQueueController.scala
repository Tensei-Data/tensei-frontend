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

import com.google.inject.Inject
import dao.{ AuthDAO, TransformationConfigurationDAO, WorkQueueDAO }
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.UserAuthority
import models.TransformationConfiguration
import org.slf4j
import play.api.Logger
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.{ Action, AnyContent, Controller }

import scala.concurrent.{ ExecutionContext, Future }

/**
  * This controller provides funtionalities to operate the work queue. The work queue
  * contains entries that describe transformation configurations that are about to be
  * processed.
  *
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param workQueueDAO The DAO for managing the work queue provided via dependency injection.
  * @param transformationConfigurationDAO The DAO for managing transformation configurations provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class WorkQueueController @Inject()(
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    workQueueDAO: WorkQueueDAO,
    transformationConfigurationDAO: TransformationConfigurationDAO,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport {
  val log: slf4j.Logger = Logger.logger

  /**
    * A function that returns a `User` object from an `Id`.
    * @todo Currently we override this method from the trait `AuthConfigImpl` because we can't use dependecy injection there to get the appropriate DAO.
    *
    * @param id  The unique database id e.g. primary key for the user.
    * @param context An implicit execution context.
    * @return An option to the user wrapped into a future.
    */
  override def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]] =
    authDAO.findAccountById(id)

  /**
    * List the entries of the work queue. Only entries that are accessible by
    * the current user are displayed.
    *
    * @return A list of work queue entries.
    */
  def index: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError())))(
        uid =>
          workQueueDAO
            .allReadable(uid, user.groupIds)
            .map(rs => Ok(views.html.dashboard.workQueue.index(rs)))
      )
  }

  /**
    * Remove the entry with the given id from the work queue.
    *
    * @param uuid The ID of the queue entry.
    * @return Redirect to the work queue list flashing success or failure or display an error.
    */
  def destroy(uuid: String): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id
        .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
          uid =>
            for {
              qo <- workQueueDAO.findById(uuid)
              to <- qo.fold(Future.successful(None: Option[TransformationConfiguration]))(
                q => transformationConfigurationDAO.findById(q.tkid)
              )
              auth <- to.fold(Future.successful(false))(
                t => authorize(user, t.getExecuteAuthorisation)
              )
              result <- qo.fold(
                Future.successful(
                  NotFound(
                    views.html.errors.notFound(Messages("errors.notfound.title"),
                                               Option(Messages("errors.notfound.header")))
                  )
                )
              )(
                q =>
                  workQueueDAO.destroy(q).map { f =>
                    if (f > 0) {
                      log.info("Work queue entry {} removed by {}.", uuid, user.email, "") // The last parameter is needed to avoid an "ambiguous reference to overloaded definition" error!
                      Redirect(routes.WorkQueueController.index()).flashing(
                        "success" -> Messages("ui.model.deleted", Messages("models.tcqueue.entry"))
                      )
                    } else {
                      Redirect(routes.WorkQueueController.index())
                        .flashing("error" -> "Could not remove entry.")
                    }
                }
              ) if auth
            } yield {
              if (auth)
                result
              else
                Forbidden(views.html.errors.forbidden())
            }
        }
  }
}
