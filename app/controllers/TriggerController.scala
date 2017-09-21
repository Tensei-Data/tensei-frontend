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

import actors.TriggerMaster.TriggerMasterMessages
import actors.{ FrontendService, TriggerMaster }
import akka.actor.{ ActorSelection, ActorSystem }
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.Inject
import com.wegtam.tensei.adt.TenseiLicenseMessages
import dao.{ AuthDAO, TransformationConfigurationDAO, TriggerDAO }
import forms.{ ResourceAuthorisationFieldsForm, TriggerForm }
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.UserAuthority
import models.{ Permission, Trigger }
import org.slf4j
import play.api.{ Configuration, Logger }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.{ Action, AnyContent, Controller, Result }
import play.filters.csrf.CSRF

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scalaz.{ Failure, Success, Validation }

/**
  * The controller provides functionalities for managing triggers.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param transformationConfigurationDAO The DAO for managing transformation configurations provided via dependency injection.
  * @param triggerDAO The DAO for managing triggers provided via dependency injection.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class TriggerController @Inject()(
    protected val configuration: Configuration,
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    transformationConfigurationDAO: TransformationConfigurationDAO,
    triggerDAO: TriggerDAO,
    implicit val system: ActorSystem,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport {

  val log: slf4j.Logger = Logger.logger

  val DEFAULT_ASK_TIMEOUT = 5000L // The fallback default timeout for `ask` operations in milliseconds.

  val frontendSelection: ActorSelection = system.actorSelection(s"/user/${FrontendService.name}")
  val triggerMaster: ActorSelection     = system.actorSelection(s"/user/${TriggerMaster.Name}")
  implicit val timeout: Timeout = Timeout(
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
    * @param context An implicit execution context.
    * @return An option to the user wrapped into a future.
    */
  override def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]] =
    authDAO.findAccountById(id)

  /**
    * Helper method that queries the frontend service for the number of allowed
    * triggers.
    *
    * @return A future holding an option to the number of allowed triggers.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def getNumberOfAllowedTriggers: Future[Option[Int]] = {
    import play.api.libs.concurrent.Execution.Implicits._

    (frontendSelection ? TenseiLicenseMessages.ReportAllowedNumberOfTriggers).map {
      case TenseiLicenseMessages.AllowedNumberOfTriggers(count) => Option(count)
      case TenseiLicenseMessages.NoLicenseInstalled             => None
      case _ =>
        log.error("Received an unexpected message while waiting for number of allowed cronjobs!")
        None
    }
  }

  /**
    * Display the form for creating a trigger.
    *
    * @return The form to add a trigger or an error page.
    */
  def add: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
      implicit token =>
        val user = loggedIn
        user.id.fold(
          Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
        ) { uid =>
          val getGroups        = authDAO.allGroups
          val getUsers         = authDAO.allUsernames
          val getTriggersCount = triggerDAO.count
          val getTransformationConfigurations =
            transformationConfigurationDAO.allReadable(uid, user.groupIds)
          for {
            as  <- getUsers
            gs  <- getGroups
            ts  <- getTransformationConfigurations
            cnt <- getTriggersCount
            max <- getNumberOfAllowedTriggers
          } yield {
            if (cnt <= max.getOrElse(0)) {
              val authorisation = ResourceAuthorisationFieldsForm.Data(
                ownerId = uid,
                groupId = user.groupIds.headOption,
                groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
                worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
              )
              val formData = TriggerForm.Data(
                tkid = 0,
                description = None,
                endpointUri = None,
                triggerTransformation = None,
                active = true,
                authorisation = authorisation
              )
              Ok(
                views.html.dashboard.triggers
                  .add(TriggerForm.form.fill(formData), canSave = true, gs, ts, as)
              )
            } else
              Redirect(routes.TriggerController.index())
                .flashing("error" -> "Maximum number of triggers reached.")
          }
        }
    }
  }

  /**
    * Try to create a trigger using the submitted form data.
    *
    * @return Redirect to the detail or list page flashing success or failure or display an error page.
    */
  def create: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
      implicit token =>
        val user = loggedIn
        user.id.fold(
          Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
        ) { uid =>
          val getGroups        = authDAO.allGroups
          val getUsers         = authDAO.allUsernames
          val getTriggersCount = triggerDAO.count
          val getTransformationConfigurations =
            transformationConfigurationDAO.allReadable(uid, user.groupIds)
          for {
            as  <- getUsers
            gs  <- getGroups
            ts  <- getTransformationConfigurations
            cnt <- getTriggersCount
            max <- getNumberOfAllowedTriggers
            ro <- TriggerForm.form
              .bindFromRequest()
              .fold(
                formWithErrors =>
                  Future.successful(
                    Failure(
                      BadRequest(
                        views.html.dashboard.triggers
                          .add(formWithErrors, canSave = cnt < max.getOrElse(0), gs, ts, as)
                      )
                    )
                ),
                formData => {
                  if (cnt < max.getOrElse(0)) {
                    val t = Trigger(
                      id = None,
                      tkid = formData.tkid,
                      description = formData.description,
                      endpointUri = formData.endpointUri,
                      triggerTkId = formData.triggerTransformation,
                      active = formData.active,
                      ownerId = formData.authorisation.ownerId,
                      groupId = formData.authorisation.groupId,
                      groupPermissions = formData.authorisation.groupPermissions,
                      worldPermissions = formData.authorisation.worldPermissions
                    )
                    transformationConfigurationDAO
                      .findById(t.tkid)
                      .map(
                        o =>
                          o.fold(
                            Failure(
                              BadRequest(
                                views.html.dashboard.triggers.add(
                                  TriggerForm.form
                                    .fill(formData)
                                    .withError("tkid", "No such transformation configuration!"),
                                  canSave = cnt < max.getOrElse(0),
                                  gs,
                                  ts,
                                  as
                                )
                              )
                            ): Validation[Result, Trigger]
                          )(
                            _ => Success(t)
                        )
                      )
                  } else {
                    Future.successful(
                      Failure(
                        Redirect(routes.TriggerController.index())
                          .flashing("error" -> "Maximum number of triggers reached.")
                      )
                    )
                  }
                }
              )
            checkTcs <- ro match {
              case Failure(f) => Future.successful(Failure(f))
              case Success(s) =>
                s.triggerTkId.fold(Future.successful(Success(s): Validation[Result, Trigger]))(
                  tid =>
                    transformationConfigurationDAO
                      .findById(tid)
                      .map(
                        o =>
                          o.fold {
                            val authorisation = ResourceAuthorisationFieldsForm.Data(
                              ownerId = s.ownerId,
                              groupId = s.groupId,
                              groupPermissions = s.groupPermissions,
                              worldPermissions = s.worldPermissions
                            )
                            val formData = TriggerForm.Data(
                              tkid = s.tkid,
                              description = s.description,
                              endpointUri = s.endpointUri,
                              triggerTransformation = s.triggerTkId,
                              active = s.active,
                              authorisation = authorisation
                            )
                            Failure(
                              BadRequest(
                                views.html.dashboard.triggers.add(
                                  TriggerForm.form
                                    .fill(formData)
                                    .withError("tkid", "No such transformation configuration!"),
                                  canSave = cnt < max.getOrElse(0),
                                  gs,
                                  ts,
                                  as
                                )
                              )
                            ): Validation[Result, Trigger]
                          } { _ =>
                            Success(s)
                        }
                    )
                )
            }
            result <- checkTcs match {
              case Failure(f) => Future.successful(f)
              case Success(s) =>
                triggerDAO.create(s).map {
                  case scala.util.Failure(e) =>
                    log.error("Could not create trigger!", e)
                    Redirect(routes.TriggerController.index())
                      .flashing("error" -> "Could not create trigger!")
                  case scala.util.Success(t) =>
                    t.id.fold(
                      Redirect(routes.TriggerController.index())
                        .flashing("error" -> "Could not create trigger!")
                    ) { id =>
                      triggerMaster ! TriggerMasterMessages.UpdateTrigger(id)
                      Redirect(routes.TriggerController.show(id))
                        .flashing("success" -> "Trigger created.")
                    }
                }
            }
          } yield result
        }
    }
  }

  /**
    * Remove the trigger with the given ID from the database.
    *
    * @param id The database id of the trigger.
    * @return Redirect to the triggers page flashing success or failure or display an error page.
    */
  def destroy(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id
        .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
          uid =>
            for {
              to <- triggerDAO.findById(id)
              auth <- to.fold(Future.successful(false))(
                t => authorize(user, t.getWriteAuthorisation)
              )
              result <- to.fold(
                Future.successful(
                  NotFound(
                    views.html.errors.notFound(Messages("errors.notfound.title"),
                                               Option(Messages("errors.notfound.header")))
                  )
                )
              )(
                t =>
                  if (auth)
                    triggerDAO
                      .destroy(t)
                      .map(
                        f =>
                          if (f > 0) {
                            triggerMaster ! TriggerMasterMessages.UpdateTrigger(id)
                            Redirect(routes.TriggerController.index())
                              .flashing("success" -> "Trigger deleted.")
                          } else
                            Redirect(routes.TriggerController.index())
                              .flashing("error" -> "Could not delete trigger.")
                      )
                  else
                    Future.successful(Forbidden(views.html.errors.forbidden()))
              )
            } yield result
        }
  }

  /**
    * Display the edit form for the trigger with the given id.
    *
    * @param id The database id of the trigger.
    * @return Redirect to the detail page flashing success or failure or an error page.
    */
  def edit(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token =>
          val user = loggedIn
          user.id.fold(
            Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
          ) { uid =>
            val getGroups        = authDAO.allGroups
            val getUsers         = authDAO.allUsernames
            val getTriggersCount = triggerDAO.count
            val getTransformationConfigurations =
              transformationConfigurationDAO.allReadable(uid, user.groupIds)
            for {
              to <- triggerDAO.findById(id)
              auth <- to.fold(Future.successful(false))(
                t => authorize(user, t.getWriteAuthorisation)
              )
              as  <- getUsers if auth
              gs  <- getGroups if auth
              ts  <- getTransformationConfigurations if auth
              cnt <- getTriggersCount if auth
              max <- getNumberOfAllowedTriggers if auth
            } yield {
              to.fold(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              )(
                t =>
                  if (auth) {
                    val authorisation = ResourceAuthorisationFieldsForm.Data(
                      ownerId = t.ownerId,
                      groupId = t.groupId,
                      groupPermissions = t.groupPermissions,
                      worldPermissions = t.worldPermissions
                    )
                    val formData = TriggerForm.Data(
                      tkid = t.tkid,
                      description = t.description,
                      endpointUri = t.endpointUri,
                      triggerTransformation = t.triggerTkId,
                      active = t.active,
                      authorisation = authorisation
                    )
                    Ok(
                      views.html.dashboard.triggers.edit(id,
                                                         TriggerForm.form.fill(formData),
                                                         canSave = cnt <= max.getOrElse(0),
                                                         gs,
                                                         ts,
                                                         as)
                    )
                  } else
                    Forbidden(views.html.errors.forbidden())
              )
            }
          }
      }
  }

  /**
    * List all triggers readable by the user.
    *
    * @return A list of all triggers that are readable by the user.
    */
  def index: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          for {
            ts  <- triggerDAO.allReadable(uid, user.groupIds)
            cs  <- transformationConfigurationDAO.allReadable(uid, user.groupIds)
            max <- getNumberOfAllowedTriggers
          } yield Ok(views.html.dashboard.triggers.index(ts, cs, max))
      }
  }

  /**
    * Show the detail page about the trigger with the given id.
    *
    * @param id The database id of the trigger.
    * @return The detail page or an error page.
    */
  def show(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id
        .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
          uid =>
            for {
              to <- triggerDAO.findById(id)
              cs <- transformationConfigurationDAO.allReadable(uid, user.groupIds)
              auth <- to
                .fold(Future.successful(false))(t => authorize(user, t.getReadAuthorisation))
            } yield {
              to.fold(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              )(
                t =>
                  if (auth)
                    Ok(views.html.dashboard.triggers.show(t, cs))
                  else
                    Forbidden(views.html.errors.forbidden())
              )
            }
        }
  }

  /**
    * Try to update the trigger with the given id using the submitted form data.
    *
    * @param id The database id of the trigger.
    * @return Redirect to the detail page flashing success or failure or an error page.
    */
  def update(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token =>
          val user = loggedIn
          user.id.fold(
            Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
          ) { uid =>
            val getGroups        = authDAO.allGroups
            val getUsers         = authDAO.allUsernames
            val getTriggersCount = triggerDAO.count
            val getTransformationConfigurations =
              transformationConfigurationDAO.allReadable(uid, user.groupIds)
            for {
              to <- triggerDAO.findById(id)
              auth <- to.fold(Future.successful(false))(
                t => authorize(user, t.getWriteAuthorisation)
              )
              as  <- getUsers if auth
              gs  <- getGroups if auth
              ts  <- getTransformationConfigurations if auth
              cnt <- getTriggersCount if auth
              max <- getNumberOfAllowedTriggers if auth
              result <- to.fold(
                Future.successful(
                  NotFound(
                    views.html.errors.notFound(Messages("errors.notfound.title"),
                                               Option(Messages("errors.notfound.header")))
                  )
                )
              )(
                t =>
                  TriggerForm.form
                    .bindFromRequest()
                    .fold(
                      formWithErrors =>
                        Future.successful(
                          BadRequest(
                            views.html.dashboard.triggers
                              .edit(id,
                                    formWithErrors,
                                    canSave = cnt <= max.getOrElse(0),
                                    gs,
                                    ts,
                                    as)
                          )
                      ),
                      formData => {
                        val r = Trigger(
                          id = Option(id),
                          tkid = formData.tkid,
                          description = formData.description,
                          endpointUri = formData.endpointUri,
                          triggerTkId = formData.triggerTransformation,
                          active = formData.active,
                          ownerId = formData.authorisation.ownerId,
                          groupId = formData.authorisation.groupId,
                          groupPermissions = formData.authorisation.groupPermissions,
                          worldPermissions = formData.authorisation.worldPermissions
                        )
                        triggerDAO.update(r).map { f =>
                          if (f > 0) {
                            triggerMaster ! TriggerMasterMessages.UpdateTrigger(id)
                            Redirect(routes.TriggerController.show(id))
                              .flashing("success" -> "Resource updated.")
                          } else
                            Redirect(routes.TriggerController.index())
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
