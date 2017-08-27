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

import actors.CronMaster.CronMasterMessages
import actors.{ CronMaster, FrontendService }
import akka.actor.ActorSystem
import akka.util.Timeout
import com.google.inject.Inject
import com.wegtam.tensei.adt.TenseiLicenseMessages
import dao.{ AuthDAO, CronDAO, TransformationConfigurationDAO }
import jp.t2v.lab.play2.auth.AuthElement
import models.{ Cron, Permission }
import play.api.{ Configuration, Logger }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.{ Controller, Result }
import akka.pattern.ask
import forms.{ CronForm, ResourceAuthorisationFieldsForm }
import models.Authorities.UserAuthority
import play.filters.csrf.CSRF

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scalaz.{ Failure, Success, Validation }

/**
  * The controller provides functionalities to manage the crontab.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param cronDAO The DAO for managing crontab entries provided via dependency injection.
  * @param transformationConfigurationDAO The DAO for handling transformation configurations provided via dependency injection.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class CronController @Inject()(
    protected val configuration: Configuration,
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    cronDAO: CronDAO,
    transformationConfigurationDAO: TransformationConfigurationDAO,
    implicit val system: ActorSystem,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport {

  val log = Logger.logger

  val DEFAULT_DB_TIMEOUT  = 10000L // The fallback default timeout for database operations in milliseconds.
  val DEFAULT_ASK_TIMEOUT = 5000L  // The fallback default timeout for `ask` operations in milliseconds.

  val frontendSelection = system.actorSelection(s"/user/${FrontendService.name}")
  val cronMaster        = system.actorSelection(s"/user/${CronMaster.Name}")
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

  /**
    * A function that returns a `User` object from an `Id`.
    * @todo Currently we override this method from the trait `AuthConfigImpl` because we can't use dependecy injection there to get the appropriate DAO.
    *
    * @param id  The unique database id e.g. primary key for the user.
    * @param context The execution context.
    * @return An option to the user wrapped into a future.
    */
  override def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]] =
    authDAO.findAccountById(id)

  /**
    * Helper method that queries the frontend service for the allowed number
    * of cronjobs.
    *
    * @return A future holding an option to the number of allowed cronjobs.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def getAllowedNumberOfCronjobs: Future[Option[Int]] = {
    import play.api.libs.concurrent.Execution.Implicits._

    (frontendSelection ? TenseiLicenseMessages.ReportAllowedNumberOfCronjobs).map {
      case TenseiLicenseMessages.AllowedNumberOfCronjobs(count) => Option(count)
      case TenseiLicenseMessages.NoLicenseInstalled             => None
      case _ =>
        log.error("Received an unexpected message while waiting for number of allowed cronjobs!")
        None
    }
  }

  /**
    * Display the form to add a new crontab entry.
    *
    * @return The form for adding a new crontab entry or an error page.
    */
  def add = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          val getConfigurations = transformationConfigurationDAO.allReadable(uid, user.groupIds)
          val getGroups         = authDAO.allGroups
          val getUsers          = authDAO.allUsernames
          val getCrontabSize    = cronDAO.count
          for {
            as  <- getUsers
            gs  <- getGroups
            ts  <- getConfigurations
            cnt <- getCrontabSize
            max <- getAllowedNumberOfCronjobs
          } yield {
            if (cnt <= max.getOrElse(0)) {
              val authorisation = ResourceAuthorisationFieldsForm.Data(
                ownerId = uid,
                groupId = user.groupIds.headOption,
                groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
                worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
              )
              val formData = CronForm.Data(
                tkid = 0,
                description = None,
                format = "",
                active = true,
                authorisation = authorisation
              )
              Ok(
                views.html.dashboard.crons
                  .add(CronForm.form.fill(formData), canSave = true, gs, ts, as)
              )
            } else
              Redirect(routes.CronController.index())
                .flashing("error" -> "Maximum number of cronjobs reached.")
          }
      }
  }

  /**
    * Try to create a crontab entry from the submitted form data.
    *
    * @return Redirect to the detail or list page flashing success or failure or display an error page.
    */
  def create = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
      implicit token =>
        val user = loggedIn
        user.id.fold(
          Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
        ) { uid =>
          val getConfigurations = transformationConfigurationDAO.allReadable(uid, user.groupIds)
          val getGroups         = authDAO.allGroups
          val getUsers          = authDAO.allUsernames
          val getCrontabSize    = cronDAO.count
          for {
            as  <- getUsers
            gs  <- getGroups
            ts  <- getConfigurations
            cnt <- getCrontabSize
            max <- getAllowedNumberOfCronjobs
            ro <- CronForm.form
              .bindFromRequest()
              .fold(
                formWithErrors =>
                  Future.successful(
                    Failure(
                      BadRequest(
                        views.html.dashboard.crons
                          .add(formWithErrors, canSave = cnt < max.getOrElse(0), gs, ts, as)
                      )
                    )
                ),
                formData => {
                  val c = Cron(
                    id = None,
                    tkid = formData.tkid,
                    description = formData.description,
                    format = formData.format,
                    active = formData.active,
                    ownerId = formData.authorisation.ownerId,
                    groupId = formData.authorisation.groupId,
                    groupPermissions = formData.authorisation.groupPermissions,
                    worldPermissions = formData.authorisation.worldPermissions
                  )
                  if (cnt < max.getOrElse(0))
                    transformationConfigurationDAO
                      .findById(c.tkid)
                      .map(
                        o =>
                          o.fold(
                            Failure(
                              BadRequest(
                                views.html.dashboard.crons.add(
                                  CronForm.form
                                    .fill(formData)
                                    .withGlobalError("Transformation configuration not found!"),
                                  canSave = cnt < max.getOrElse(0),
                                  gs,
                                  ts,
                                  as
                                )
                              )
                            ): Validation[Result, Cron]
                          )(t => Success(c))
                      )
                  else
                    Future.successful(
                      Failure(
                        Redirect(routes.CronController.index())
                          .flashing("error" -> "Maximum number of cronjobs reached.")
                      )
                    )
                }
              )
            result <- ro match {
              case Failure(f) => Future.successful(f)
              case Success(s) =>
                cronDAO.create(s).map {
                  case scala.util.Failure(e) =>
                    log.error("Could not create cron resource!", e)
                    Redirect(routes.CronController.index())
                      .flashing("error" -> "Could not create resource!")
                  case scala.util.Success(c) =>
                    c.id.fold(
                      Redirect(routes.CronController.index())
                        .flashing("error" -> "Could not create resource!")
                    ) { id =>
                      cronMaster ! CronMasterMessages.UpdateCron(id)
                      Redirect(routes.CronController.show(id))
                        .flashing("success" -> "Created resource.")
                    }
                }
            }
          } yield result
        }
    }
  }

  /**
    * Destroy the crontab entry with the given ID.
    *
    * @param id The ID of a crontab entry.
    * @return Redirect to the crontab list flashing success or failure or display an error page.
    */
  def destroy(id: Long) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError())))(
        uid =>
          for {
            co <- cronDAO.findById(id)
            check <- co.fold(
              Future.successful(
                Failure(
                  NotFound(
                    views.html.errors.notFound(Messages("errors.notfound.title"),
                                               Option(Messages("errors.notfound.header")))
                  )
                )
              ): Future[Validation[Result, Cron]]
            )(
              c =>
                authorize(user, c.getWriteAuthorisation).map(
                  can =>
                    if (can)
                      Success(c)
                    else
                      Failure(Forbidden(views.html.errors.forbidden()))
              )
            )
            result <- check match {
              case Failure(f) => Future.successful(f)
              case Success(s) =>
                cronDAO
                  .destroy(s)
                  .map(
                    r =>
                      if (r > 0) {
                        cronMaster ! CronMasterMessages.UpdateCron(id)
                        Redirect(routes.CronController.index()).flashing(
                          "success" -> Messages("ui.model.deleted", Messages("models.cron"))
                        )
                      } else
                        Redirect(routes.CronController.index())
                          .flashing("error" -> "No entry was deleted.")
                  )
            }
          } yield result
      )
  }

  /**
    * Display the edit form for a crontab entry.
    *
    * @param id The ID of a crontab entry.
    * @return The edit form or an error page.
    */
  def edit(id: Long) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
      implicit token =>
        val user = loggedIn
        user.id.fold(
          Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
        ) { uid =>
          val getConfigurations = transformationConfigurationDAO.allReadable(uid, user.groupIds)
          val getGroups         = authDAO.allGroups
          val getUsers          = authDAO.allUsernames
          val getCrontabSize    = cronDAO.count
          for {
            co   <- cronDAO.findById(id)
            as   <- getUsers if co.isDefined
            gs   <- getGroups if co.isDefined
            ts   <- getConfigurations if co.isDefined
            cnt  <- getCrontabSize
            max  <- getAllowedNumberOfCronjobs
            auth <- co.fold(Future.successful(false))(c => authorize(user, c.getWriteAuthorisation))
          } yield {
            co.fold(
              NotFound(
                views.html.errors.notFound(Messages("errors.notfound.title"),
                                           Option(Messages("errors.notfound.header")))
              )
            ) { c =>
              if (auth) {
                val authorisation = ResourceAuthorisationFieldsForm.Data(
                  ownerId = c.ownerId,
                  groupId = c.groupId,
                  groupPermissions = c.groupPermissions,
                  worldPermissions = c.worldPermissions
                )
                val formData = CronForm.Data(
                  tkid = c.tkid,
                  description = c.description,
                  format = c.format,
                  active = c.active,
                  authorisation = authorisation
                )
                Ok(
                  views.html.dashboard.crons.edit(id,
                                                  CronForm.form.fill(formData),
                                                  canSave = cnt <= max.getOrElse(0),
                                                  gs,
                                                  ts,
                                                  as)
                )
              } else
                Forbidden(views.html.errors.forbidden())
            }
          }
        }
    }
  }

  /**
    * List all cronjobs from the database that the current user has permission to see.
    *
    * @return A list of all cronjobs readable by the user.
    */
  def index = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError())))(
        uid =>
          for {
            cs  <- cronDAO.allReadable(uid, user.groupIds)
            max <- getAllowedNumberOfCronjobs
          } yield Ok(views.html.dashboard.crons.index(cs, max))
      )
  }

  /**
    * Show the crontab entry with the given id.
    *
    * @param id The ID of the crontab entry.
    * @return The detail page of the crontab entry or an error page.
    */
  def show(id: Long) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError())))(
        uid =>
          for {
            co <- cronDAO.findById(id)
            result <- co.fold(
              Future.successful(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              )
            )(
              c =>
                authorize(user, c.getReadAuthorisation).map(
                  can =>
                    if (can)
                      Ok(views.html.dashboard.crons.show(c))
                    else
                      Forbidden(views.html.errors.forbidden())
              )
            )
          } yield result
      )
  }

  /**
    * Try to update the crontab entry with the given ID using the submitted form data.
    *
    * @param id The ID of the crontab entry.
    * @return Redirect to the detail page flashing success or failure or display an error page.
    */
  def update(id: Long) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
      implicit token =>
        val user = loggedIn
        user.id.fold(
          Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
        ) { uid =>
          val getConfigurations = transformationConfigurationDAO.allReadable(uid, user.groupIds)
          val getGroups         = authDAO.allGroups
          val getUsers          = authDAO.allUsernames
          val getCrontabSize    = cronDAO.count
          for {
            co   <- cronDAO.findById(id)
            as   <- getUsers if co.isDefined
            gs   <- getGroups if co.isDefined
            ts   <- getConfigurations if co.isDefined
            cnt  <- getCrontabSize
            max  <- getAllowedNumberOfCronjobs
            auth <- co.fold(Future.successful(false))(c => authorize(user, c.getWriteAuthorisation))
            result <- co.fold(
              Future.successful(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              )
            )(
              c =>
                CronForm.form
                  .bindFromRequest()
                  .fold(
                    formWithErrors =>
                      Future.successful(
                        BadRequest(
                          views.html.dashboard.crons
                            .edit(id, formWithErrors, canSave = cnt <= max.getOrElse(0), gs, ts, as)
                        )
                    ),
                    formData => {
                      val r = Cron(
                        id = Option(id),
                        tkid = formData.tkid,
                        description = formData.description,
                        format = formData.format,
                        active = formData.active,
                        ownerId = formData.authorisation.ownerId,
                        groupId = formData.authorisation.groupId,
                        groupPermissions = formData.authorisation.groupPermissions,
                        worldPermissions = formData.authorisation.worldPermissions
                      )
                      cronDAO.update(r).map { f =>
                        if (f > 0) {
                          cronMaster ! CronMasterMessages.UpdateCron(id)
                          Redirect(routes.CronController.show(id))
                            .flashing("success" -> "Resource updated.")
                        } else
                          Redirect(routes.CronController.index())
                            .flashing("error" -> "Update failed.")
                      }
                    }
                )
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

}
