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
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import argonaut.Argonaut._
import argonaut._
import com.google.inject.Inject
import com.wegtam.tensei.adt.TenseiLicenseMessages
import dao._
import forms.{ ResourceAuthorisationFieldsForm, TransformationConfigurationForm }
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.UserAuthority
import models._
import org.dfasdl.utils.{ DocumentHelpers, ElementHelpers }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.{ Controller, Result }
import play.api.{ Configuration, Logger }
import play.filters.csrf.CSRF

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scalaz.{ Failure, Success, Validation }

/**
  * The controller provides functionalities to manage transformation configurations.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param workQueueDAO The DAO for managing the work queue provided via dependency injection.
  * @param connectionInformationResourceDAO The DAO for handling connection informations provided via dependency injection.
  * @param cookbookResourceDAO The DAO for handling cookbook resources provided via dependency injection.
  * @param dfasdlResourceDAO The DAO for handling dfasdl resources provided via dependency injection.
  * @param transformationConfigurationDAO The DAO for managing transformation configurations provided via dependency injection.
  * @param workHistoryDAO The DAO for managing the work history provided via dependency injection.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class TransformationConfigurationsController @Inject()(
    protected val configuration: Configuration,
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    workQueueDAO: WorkQueueDAO,
    connectionInformationResourceDAO: ConnectionInformationResourceDAO,
    cookbookResourceDAO: CookbookResourceDAO,
    dfasdlResourceDAO: DFASDLResourceDAO,
    transformationConfigurationDAO: TransformationConfigurationDAO,
    workHistoryDAO: WorkHistoryDAO,
    implicit val system: ActorSystem,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport
    with DocumentHelpers
    with ElementHelpers {
  val DEFAULT_DB_TIMEOUT  = 10000L // The fallback default timeout for database operations in milliseconds.
  val DEFAULT_ASK_TIMEOUT = 5000L  // The fallback default timeout for `ask` operations in milliseconds.

  val log = Logger.logger

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
    * Helper method that queries the frontend service for the number of allowed
    * transformation configurations.
    *
    * @return A future holding an option to the number of allowed transformation configurations.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def getNumberOfAllowedConfigurations: Future[Option[Int]] = {
    import play.api.libs.concurrent.Execution.Implicits._

    (frontendSelection ? TenseiLicenseMessages.ReportAllowedNumberOfConfigurations).map {
      case TenseiLicenseMessages.AllowedNumberOfConfigurations(count) => Option(count)
      case TenseiLicenseMessages.NoLicenseInstalled                   => None
      case _ =>
        log.error(
          "Received an unexpected message while waiting for number of allowed transformation configurations!"
        )
        None
    }
  }

  /**
    * Display the form for adding a new transformation configuration.
    *
    * @return The form for adding a new transformation configuration or an error page.
    */
  def add = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          val getConnections = connectionInformationResourceDAO.allReadable(uid, user.groupIds)
          val getCookbooks =
            cookbookResourceDAO.allReadable(uid, user.groupIds)(loadCookbooks = true)
          val getGroups                    = authDAO.allGroups
          val getUsers                     = authDAO.allUsernames
          val getTransformationConfigsSize = transformationConfigurationDAO.count
          for {
            as  <- getUsers
            gs  <- getGroups
            cis <- getConnections
            cbs <- getCookbooks
            cnt <- getTransformationConfigsSize
            max <- getNumberOfAllowedConfigurations
          } yield {
            if (cnt < max.getOrElse(0)) {
              val authorisation = ResourceAuthorisationFieldsForm.Data(
                ownerId = uid,
                groupId = user.groupIds.headOption,
                groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
                worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
              )
              val formData = TransformationConfigurationForm.Data(
                name = None,
                cookbookResourceId = 0,
                sources = List.empty,
                target = DfasdlConnectionMapping(dfasdlId = 0, connectionInformationId = 0),
                authorisation = authorisation
              )
              Ok(
                views.html.dashboard.transformationconfigurations.add(
                  TransformationConfigurationForm.form.fill(formData),
                  canSave = cnt < max.getOrElse(0),
                  cis,
                  cbs,
                  Map.empty,
                  gs,
                  as
                )
              )
            } else
              Redirect(routes.TransformationConfigurationsController.index())
                .flashing("error" -> "Maximum number of transformation configurations reached.")
          }
      }
  }

  /**
    * Try to create a transformation configuration using the submitted form data.
    *
    * @return Redirect to the detail page or display an error.
    */
  def create = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
      implicit token =>
        val user = loggedIn
        user.id.fold(
          Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
        ) { uid =>
          val getConnections = connectionInformationResourceDAO.allReadable(uid, user.groupIds)
          val getCookbooks =
            cookbookResourceDAO.allReadable(uid, user.groupIds)(loadCookbooks = false)
          val getDfasdls                   = dfasdlResourceDAO.allReadable(uid, user.groupIds)(loadDfasdls = true)
          val getGroups                    = authDAO.allGroups
          val getUsers                     = authDAO.allUsernames
          val getTransformationConfigsSize = transformationConfigurationDAO.count
          for {
            as      <- getUsers
            gs      <- getGroups
            cis     <- getConnections
            cbs     <- getCookbooks
            cnt     <- getTransformationConfigsSize
            max     <- getNumberOfAllowedConfigurations
            dfasdls <- getDfasdls
            dfasdlIdMappings <- Future.successful(
              dfasdls.flatMap(d => d.id.map(id => id.toString -> d.dfasdl.id)).toMap
            )
            check <- TransformationConfigurationForm.form
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  Future.successful(
                    Failure(
                      BadRequest(
                        views.html.dashboard.transformationconfigurations.add(formWithErrors,
                                                                              canSave = cnt < max
                                                                                .getOrElse(0),
                                                                              cis,
                                                                              cbs,
                                                                              dfasdlIdMappings,
                                                                              gs,
                                                                              as)
                      )
                    ): Validation[Result, TransformationConfigurationForm.Data]
                  )
                },
                formData => {
                  if (max.getOrElse(0) < cnt)
                    Future.successful(
                      Failure(
                        Redirect(routes.TransformationConfigurationsController.index()).flashing(
                          "error" -> "Maximum number of transformation configurations reached."
                        )
                      )
                    )
                  else {
                    Future.successful(Success(formData))
                  }
                }
              )
            cbo <- check match {
              case Failure(f) => Future.successful(None: Option[CookbookResource])
              case Success(s) =>
                cookbookResourceDAO.findById(s.cookbookResourceId)(loadCookbook = true)
            }
            result <- check match {
              case Failure(f) => Future.successful(f)
              case Success(s) =>
                cbo.fold(
                  Future.successful(
                    BadRequest(
                      views.html.dashboard.transformationconfigurations.add(
                        TransformationConfigurationForm.form
                          .fill(s)
                          .withError("cookbookResource", "Cookbook not found!"),
                        canSave = cnt < max.getOrElse(0),
                        cis,
                        cbs,
                        dfasdlIdMappings,
                        gs,
                        as
                      )
                    )
                  )
                ) { cr =>
                  val tc = TransformationConfiguration(
                    id = None,
                    name = s.name,
                    sourceConnections = s.sources,
                    targetConnection = s.target,
                    cookbook = cr,
                    dirty = false,
                    ownerId = s.authorisation.ownerId,
                    groupId = s.authorisation.groupId,
                    groupPermissions = s.authorisation.groupPermissions,
                    worldPermissions = s.authorisation.worldPermissions
                  )
                  transformationConfigurationDAO.create(tc).map {
                    case scala.util.Failure(e) =>
                      log.error("Could not create transformation configuration!", e)
                      Redirect(routes.TransformationConfigurationsController.index())
                        .flashing("error" -> "Could not create transformation configuration!")
                    case scala.util.Success(t) =>
                      t.id.fold(
                        Redirect(routes.TransformationConfigurationsController.index()).flashing(
                          "error" -> "An unexpected error occured while trying to create the transformation configuration!"
                        )
                      )(
                        id =>
                          Redirect(routes.TransformationConfigurationsController.show(id))
                            .flashing("success" -> "Created transformation configuration.")
                      )
                  }
                }
            }
          } yield result
        }
    }
  }

  /**
    * Delete the transformation configuration with the given id.
    *
    * @param id The database id of a transformation configuration.
    * @return Redirect to the index page flashing success or failure or an error page.
    */
  def destroy(id: Long) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          for {
            to   <- transformationConfigurationDAO.findById(id)
            auth <- to.fold(Future.successful(false))(t => authorize(user, t.getWriteAuthorisation))
            result <- to.fold(
              Future.successful(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              )
            )(
              t =>
                transformationConfigurationDAO
                  .destroy(t)
                  .map(
                    f =>
                      if (f > 0)
                        Redirect(routes.TransformationConfigurationsController.index())
                          .flashing("success" -> "Transformation configuration deleted.")
                      else
                        Redirect(routes.TransformationConfigurationsController.index())
                          .flashing("error" -> "Could not delete transformation configuration!.")
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

  /**
    * Display the form for editing the transformation configuration with the given id.
    *
    * @param id The database id of the transformation configuration.
    * @return The form or an error page.
    */
  def edit(id: Long) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          val getConnections = connectionInformationResourceDAO.allReadable(uid, user.groupIds)
          val getCookbooks =
            cookbookResourceDAO.allReadable(uid, user.groupIds)(loadCookbooks = false)
          val getGroups                    = authDAO.allGroups
          val getUsers                     = authDAO.allUsernames
          val getTransformationConfigsSize = transformationConfigurationDAO.count
          for {
            to   <- transformationConfigurationDAO.findById(id)
            auth <- to.fold(Future.successful(false))(t => authorize(user, t.getWriteAuthorisation))
            dfasdls <- to.fold(Future.successful(Seq.empty[DFASDLResource]))(
              t =>
                dfasdlResourceDAO.findByIds(
                  t.sourceConnections.map(_.dfasdlId).toSet + t.targetConnection.dfasdlId
                )(loadDfasdl = true)
            )
            dfasdlIdMappings <- Future.successful(
              dfasdls.flatMap(d => d.id.map(id => id.toString -> d.dfasdl.id)).toMap
            )
            as  <- getUsers if auth
            gs  <- getGroups if auth
            cis <- getConnections if auth
            cbs <- getCookbooks if auth
            cnt <- getTransformationConfigsSize
            max <- getNumberOfAllowedConfigurations
          } yield {
            if (auth) {
              to.fold(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              ) { t =>
                val authorisation = ResourceAuthorisationFieldsForm.Data(
                  ownerId = t.ownerId,
                  groupId = t.groupId,
                  groupPermissions = t.groupPermissions,
                  worldPermissions = t.worldPermissions
                )
                val td = TransformationConfigurationForm.Data(
                  name = t.name,
                  cookbookResourceId = t.cookbook.id.get,
                  sources = t.sourceConnections.toList,
                  target = t.targetConnection,
                  authorisation = authorisation
                )
                val formData =
                  if (t.dirty)
                    td.copy(cookbookResourceId = 0L,
                            sources = List.empty,
                            target =
                              DfasdlConnectionMapping(dfasdlId = 0L, connectionInformationId = 0L))
                  else td
                Ok(
                  views.html.dashboard.transformationconfigurations.edit(
                    id,
                    TransformationConfigurationForm.form.fill(formData),
                    canSave = cnt < max.getOrElse(0),
                    cis,
                    cbs,
                    dfasdlIdMappings,
                    gs,
                    as
                  )
                )
              }
            } else
              Forbidden(views.html.errors.forbidden())
          }
      }
  }

  /**
    * A helper that generates json data that is used to update important parts of the form
    * for creating/editing transformation configurations if the cookbook is changed.
    *
    * @param cookbookId The id of the cookbook that is selected.
    * @return JSON data for the on page javascript that is used to update the form.
    */
  def generateFormData(cookbookId: Long) = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(
        Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
      ) { uid =>
        val getConnections = connectionInformationResourceDAO.allReadable(uid, user.groupIds)
        for {
          co   <- cookbookResourceDAO.findById(cookbookId)(loadCookbook = true)
          auth <- co.fold(Future.successful(false))(c => authorize(user, c.getReadAuthorisation))
          cs <- if (auth) getConnections
          else Future.successful(Seq.empty[ConnectionInformationResource])
          ids <- co.fold(Future.successful(Set.empty[String]))(
            c =>
              Future.successful(
                c.cookbook.target
                  .fold(Set.empty[String])(t => Set(t.id)) ++ c.cookbook.sources.map(_.id).toSet
            )
          )
          dfasdls <- dfasdlResourceDAO.findByDfasdlIds(ids)
        } yield {
          render {
            case Accepts.Html() => UnsupportedMediaType("Please use application/json.")
            case Accepts.Json() =>
              if (auth) {
                co.fold(NotFound("The requested resource was not found!").as("application/json"))(
                  c =>
                    c.id.fold(
                      InternalServerError("An unexpected error occured!").as("application/json")
                    ) { cid =>
                      val sourceMappings = c.cookbook.sources.flatMap { d =>
                        dfasdls
                          .find(_.dfasdl.id == d.id)
                          .fold(None: Option[DfasdlConnectionMapping])(
                            r =>
                              r.id.fold(None: Option[DfasdlConnectionMapping])(
                                rid =>
                                  Option(
                                    DfasdlConnectionMapping(dfasdlId = rid,
                                                            connectionInformationId = 0)
                                )
                            )
                          )
                      }
                      val targetMapping = c.cookbook.target.flatMap(
                        t =>
                          dfasdls
                            .find(_.dfasdl.id == t.id)
                            .fold(None: Option[DfasdlConnectionMapping])(
                              r =>
                                r.id.fold(None: Option[DfasdlConnectionMapping])(
                                  rid =>
                                    Option(
                                      DfasdlConnectionMapping(dfasdlId = rid,
                                                              connectionInformationId = 0)
                                  )
                              )
                          )
                      )
                      val dfasdlMappings: Map[String, String] = dfasdls
                        .flatMap(
                          d =>
                            d.id.fold(None: Option[(String, String)])(
                              did => Option(did.toString -> d.dfasdl.id)
                          )
                        )
                        .toMap
                      val sourceForms = sourceMappings.map(
                        m => TransformationConfigurationForm.dfasdlConnectionMappingForm.fill(m)
                      )
                      val renderedSourceForms = views.html.dashboard.transformationconfigurations
                        .sourceConnectionsForm(sourceForms, cs, dfasdlMappings)
                        .toString()
                        .trim
                      val renderedTargetForm = targetMapping.fold("")(
                        m =>
                          views.html.dashboard.transformationconfigurations
                            .targetConnectionForm(
                              TransformationConfigurationForm.dfasdlConnectionMappingForm.fill(m),
                              cs,
                              dfasdlMappings
                            )
                            .toString()
                            .trim
                      )

                      val jsonData: Json = Json(
                        "sourceMappings" := renderedSourceForms,
                        "targetMapping" := renderedTargetForm
                      )

                      Ok(jsonData.nospaces).as("application/json")
                  }
                )
              } else
                Forbidden("You are not authorised to access this resource!").as("application/json")
          }
        }
      }
  }

  /**
    * List all transformation configurations readable by the user.
    *
    * @return A list of transformation configurations.
    */
  def index = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          val getTransformations = transformationConfigurationDAO.allReadable(uid, user.groupIds)
          val getConnections     = connectionInformationResourceDAO.allReadable(uid, user.groupIds)
          for {
            ts  <- getTransformations
            max <- getNumberOfAllowedConfigurations
            cs  <- getConnections
          } yield {
            Ok(views.html.dashboard.transformationconfigurations.index(ts, max, cs))
          }
      }
  }

  /**
    * Show the details for the transformation configuration with the given id.
    *
    * @param id The database id of the transformation configuration.
    * @return The detail page or an error.
    */
  def show(id: Long) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          for {
            to   <- transformationConfigurationDAO.findById(id)
            auth <- to.fold(Future.successful(false))(t => authorize(user, t.getReadAuthorisation))
            cs <- if (auth) connectionInformationResourceDAO.allReadable(uid, user.groupIds)
            else Future.successful(Seq.empty[ConnectionInformationResource])
          } yield {
            render {
              case Accepts.Html() =>
                to.fold(
                  NotFound(
                    views.html.errors.notFound(Messages("errors.notfound.title"),
                                               Option(Messages("errors.notfound.header")))
                  )
                ) { t =>
                  if (auth)
                    Ok(views.html.dashboard.transformationconfigurations.show(t, cs))
                  else
                    Forbidden(views.html.errors.forbidden())
                }
              case Accepts.Json() =>
                to.fold(NotFound("The requested resource was not found!").as("application/json")) {
                  t =>
                    if (auth)
                      Ok(t.asJson.nospaces).as("application/json")
                    else
                      Forbidden("You are not authorised to access this resource!").as(
                        "application/json"
                      )
                }
            }
          }
      }
  }

  /**
    * Try to update the transformation configuration with the given id using
    * the submitted form data.
    *
    * @param id The database id of a transformation configuration.
    * @return Redirect to the detail page flashing success or failure or an error page.
    */
  def update(id: Long) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          val getConnections = connectionInformationResourceDAO.allReadable(uid, user.groupIds)
          val getCookbooks =
            cookbookResourceDAO.allReadable(uid, user.groupIds)(loadCookbooks = false)
          val getDfasdls                   = dfasdlResourceDAO.allReadable(uid, user.groupIds)(loadDfasdls = true)
          val getGroups                    = authDAO.allGroups
          val getUsers                     = authDAO.allUsernames
          val getTransformationConfigsSize = transformationConfigurationDAO.count
          for {
            to      <- transformationConfigurationDAO.findById(id)
            auth    <- to.fold(Future.successful(false))(t => authorize(user, t.getWriteAuthorisation))
            dfasdls <- getDfasdls
            dfasdlIdMappings <- Future.successful(
              dfasdls.flatMap(d => d.id.map(id => id.toString -> d.dfasdl.id)).toMap
            )
            as  <- getUsers if auth
            gs  <- getGroups if auth
            cis <- getConnections if auth
            cbs <- getCookbooks if auth
            cnt <- getTransformationConfigsSize
            max <- getNumberOfAllowedConfigurations
            check <- TransformationConfigurationForm.form
              .bindFromRequest()
              .fold(
                formWithErrors =>
                  Future.successful(
                    Failure(
                      BadRequest(
                        views.html.dashboard.transformationconfigurations.add(formWithErrors,
                                                                              canSave = cnt < max
                                                                                .getOrElse(0),
                                                                              cis,
                                                                              cbs,
                                                                              dfasdlIdMappings,
                                                                              gs,
                                                                              as)
                      )
                    ): Validation[Result, TransformationConfigurationForm.Data]
                ),
                formData => {
                  if (cnt < max.getOrElse(0))
                    Future.successful(Success(formData))
                  else
                    Future.successful(
                      Failure(
                        Redirect(routes.TransformationConfigurationsController.index()).flashing(
                          "error" -> "Maximum number of transformation configurations reached."
                        )
                      )
                    )
                }
              )
            cbo <- check match {
              case Failure(f) => Future.successful(None: Option[CookbookResource])
              case Success(s) =>
                cookbookResourceDAO.findById(s.cookbookResourceId)(loadCookbook = true)
            }
            result <- check match {
              case Failure(f) => Future.successful(f)
              case Success(s) =>
                cbo.fold(
                  Future.successful(
                    BadRequest(
                      views.html.dashboard.transformationconfigurations.add(
                        TransformationConfigurationForm.form
                          .fill(s)
                          .withError("cookbookResource", "Cookbook not found!"),
                        canSave = cnt < max.getOrElse(0),
                        cis,
                        cbs,
                        dfasdlIdMappings,
                        gs,
                        as
                      )
                    )
                  )
                ) { cr =>
                  val tc = TransformationConfiguration(
                    id = Option(id),
                    name = s.name,
                    sourceConnections = s.sources,
                    targetConnection = s.target,
                    cookbook = cr,
                    dirty = false,
                    ownerId = s.authorisation.ownerId,
                    groupId = s.authorisation.groupId,
                    groupPermissions = s.authorisation.groupPermissions,
                    worldPermissions = s.authorisation.worldPermissions
                  )
                  transformationConfigurationDAO
                    .update(tc)
                    .map(
                      f =>
                        if (f > 0)
                          Redirect(routes.TransformationConfigurationsController.show(id))
                            .flashing("success" -> "Updated transformation configuration.")
                        else
                          Redirect(routes.TransformationConfigurationsController.index())
                            .flashing("error" -> "Could not update transformation configuration!")
                    )
                }
            }
          } yield result
      }
  }

}
