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

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

import actors.FrontendService
import actors.FrontendService.FrontendServiceMessages
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import argonaut.Argonaut._
import com.google.inject.Inject
import com.wegtam.tensei.adt.{ DFASDL, ExtractSchemaOptions, GlobalMessages }
import dao.{ AuthDAO, ConnectionInformationResourceDAO, DFASDLResourceDAO }
import forms.{ DfasdlResourceForm, ExtractDfasdlForm, ResourceAuthorisationFieldsForm }
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.UserAuthority
import models.{ ChartDataEntry, DFASDLResource, DfasdlStatistics, Permission }
import org.dfasdl.utils.{ AttributeNames, DocumentHelpers, ElementHelpers, ElementNames }
import org.w3c.dom.{ Document, Element }
import org.w3c.dom.traversal.{ DocumentTraversal, NodeFilter, NodeIterator }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.libs.json.JsNumber
import play.api.mvc._
import play.api.{ Configuration, Logger }
import play.filters.csrf.CSRF

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import scalaz._

/**
  * This controller provides functionalities for managing DFASDL resources.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param dfasdlResourceDAO The DAO for managing DFASDL resources provided via dependency injection.
  * @param connectionInformationResourceDAO The DAO for managing connection information resources provided via dependency injection.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class DFASDLResourcesController @Inject()(
    protected val configuration: Configuration,
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    dfasdlResourceDAO: DFASDLResourceDAO,
    connectionInformationResourceDAO: ConnectionInformationResourceDAO,
    implicit val system: ActorSystem,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport
    with DocumentHelpers {
  val DEFAULT_ASK_TIMEOUT     = 5000L   // The fallback default timeout for `ask` operations in milliseconds.
  val DEFAULT_EXTRACT_TIMEOUT = 120000L // The fallback default timeout for schema extraction operations in milliseconds.

  private val log = Logger.logger

  private val frontendSelection = system.actorSelection(s"/user/${FrontendService.name}")
  implicit val timeout = Timeout(
    FiniteDuration(
      configuration.getMilliseconds("tensei.frontend.ask-timeout").getOrElse(DEFAULT_ASK_TIMEOUT),
      MILLISECONDS
    )
  )
  lazy val extractSchemaTimeout = Timeout(
    FiniteDuration(configuration
                     .getMilliseconds("tensei.frontend.extract-schema-timeout")
                     .getOrElse(DEFAULT_EXTRACT_TIMEOUT),
                   MILLISECONDS)
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
    * Display the form to add a new dfasdl resource.
    *
    * @return The form or an error page.
    */
  def add: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          val getGroups = authDAO.allGroups
          val getUsers  = authDAO.allUsernames
          for {
            as <- getUsers
            gs <- getGroups
          } yield {
            val authorisation = ResourceAuthorisationFieldsForm.Data(
              ownerId = uid,
              groupId = user.groupIds.headOption,
              groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
              worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
            )
            val formData = DfasdlResourceForm.Data(
              dfasdl = DFASDL(
                id = "",
                content = DFASDLResourcesController.DfasdlContentPlaceholder,
                version = "1"
              ),
              authorisation = authorisation
            )
            Ok(
              views.html.dashboard.dfasdlresources
                .add(DfasdlResourceForm.form.fill(formData), gs, as)
            )
          }
      }
  }

  /**
    * Try to create a new dfasdl resource in the database using the submitted form data.
    *
    * @return Redirect to the dfasdl page or display an error.
    */
  def create: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          val getGroups = authDAO.allGroups
          val getUsers  = authDAO.allUsernames
          for {
            as <- getUsers
            gs <- getGroups
            ro <- DfasdlResourceForm.form
              .bindFromRequest()
              .fold(
                formWithErrors =>
                  Future.successful(
                    Failure(
                      BadRequest(views.html.dashboard.dfasdlresources.add(formWithErrors, gs, as))
                    )
                ),
                formData => {
                  val resource = DFASDLResource(
                    id = None,
                    dfasdl = formData.dfasdl,
                    ownerId = formData.authorisation.ownerId,
                    groupId = formData.authorisation.groupId,
                    groupPermissions = formData.authorisation.groupPermissions,
                    worldPermissions = formData.authorisation.worldPermissions
                  )
                  dfasdlResourceDAO
                    .findByDfasdlId(formData.dfasdl.id)
                    .map(
                      o =>
                        o.fold(Success(resource): Validation[Result, DFASDLResource])(
                          d =>
                            Failure(
                              BadRequest(
                                views.html.dashboard.dfasdlresources
                                  .add(DfasdlResourceForm.form
                                         .fill(formData)
                                         .withError("dfasdl.id", "A DFASDL name must be unique!"),
                                       gs,
                                       as)
                              )
                          )
                      )
                    )
                }
              )
            result <- ro match {
              case Failure(f) => Future.successful(f)
              case Success(s) =>
                val validationResult: Try[scala.collection.immutable.Set[String]] = for {
                  b <- Try(createDocumentBuilder())
                  d <- Try(
                    b.parse(
                      new ByteArrayInputStream(s.dfasdl.content.getBytes(StandardCharsets.UTF_8))
                    )
                  )
                  ids <- DFASDLResourcesController.calculateReservedSQLKeywords(d)
                } yield ids
                val validationError: String = validationResult match {
                  case scala.util.Failure(error) =>
                    Messages("validate.dfasdl.on-save.error", error.getMessage.trim)
                  case scala.util.Success(reservedNames) =>
                    if (reservedNames.nonEmpty)
                      Messages("validate.dfasdl.reservedNames", reservedNames.mkString(", "))
                    else
                      ""
                }
                dfasdlResourceDAO.create(s).map {
                  case scala.util.Failure(e) =>
                    log.error("Could not create dfasdl resource!", e)
                    Redirect(routes.DFASDLResourcesController.index())
                      .flashing("error" -> "Could not create resource!")
                  case scala.util.Success(r) =>
                    r.id.fold(
                      Redirect(routes.DFASDLResourcesController.index())
                        .flashing("error" -> "Could not create resource!")
                    )(
                      id =>
                        Redirect(routes.DFASDLResourcesController.show(id))
                          .flashing(
                            "success" -> "Created resource.",
                            "warning" -> validationError
                        )
                    )
                }
            }
          } yield result
      }
  }

  /**
    * Destroy the dfasdl resource with the given ID in the database.
    *
    * @param id The ID of the dfasdl resource.
    * @return Redirect to the dfasdls page flashing success or failure or display an error page.
    */
  def destroy(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id
        .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
          uid =>
            for {
              ro <- dfasdlResourceDAO.findById(id)(loadDfasdl = false)
              auth <- ro.fold(Future.successful(false))(
                r => authorize(user, r.getWriteAuthorisation)
              )
              result <- ro.fold(Future.successful(0))(r => dfasdlResourceDAO.destroy(r)) if auth
            } yield {
              ro.fold(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              ) { r =>
                if (result > 0)
                  Redirect(routes.DFASDLResourcesController.index()).flashing(
                    "success" -> Messages("ui.model.deleted", Messages("models.dfasdlresource"))
                  )
                else
                  Redirect(routes.DFASDLResourcesController.index())
                    .flashing("error" -> "No entry was deleted.")
              }
            }
        }
  }

  /**
    * Display the form to edit the dfasdl resource with the given id.
    *
    * @param id The database ID of the dfasdl resource.
    * @return The form or an error page.
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
            val getGroups   = authDAO.allGroups
            val getUsers    = authDAO.allUsernames
            val getResource = dfasdlResourceDAO.findById(id)(loadDfasdl = true)
            for {
              ro <- getResource
              as <- getUsers if ro.isDefined
              gs <- getGroups if ro.isDefined
              auth <- ro
                .fold(Future.successful(false))(r => authorize(user, r.getWriteAuthorisation))
            } yield {
              ro.fold(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              ) { r =>
                if (auth) {
                  val authorisation = ResourceAuthorisationFieldsForm.Data(
                    ownerId = r.ownerId,
                    groupId = r.groupId,
                    groupPermissions = r.groupPermissions,
                    worldPermissions = r.worldPermissions
                  )
                  val formData = DfasdlResourceForm.Data(
                    dfasdl = r.dfasdl,
                    authorisation = authorisation
                  )
                  Ok(
                    views.html.dashboard.dfasdlresources
                      .edit(id, DfasdlResourceForm.form.fill(formData), gs, as)
                  )
                } else
                  Forbidden(views.html.errors.forbidden())
              }
            }
          }
      }
  }

  /**
    * Return the database id of the dfasdl resource that contains the DFASDL with the given
    * name (ID).
    *
    * @param name The DFASDL id e.g. the name of it.
    * @return The id of the dfasdl resource or an error page.
    */
  def getIdFromName(name: String): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id
        .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
          uid =>
            for {
              ro <- dfasdlResourceDAO.findByDfasdlId(name)
              auth <- ro
                .fold(Future.successful(false))(r => authorize(user, r.getReadAuthorisation))
            } yield {
              render {
                case Accepts.Html() =>
                  if (auth)
                    ro.fold(
                      NotFound(
                        views.html.errors.notFound(Messages("errors.notfound.title"),
                                                   Option(Messages("errors.notfound.header")))
                      )
                    )(
                      r =>
                        r.id.fold(InternalServerError(views.html.dashboard.errors.serverError()))(
                          id => Ok(id.toString)
                      )
                    )
                  else
                    Forbidden(views.html.errors.forbidden())
                case Accepts.Json() =>
                  if (auth)
                    ro.fold(
                      NotFound("The requested resource was not found!").as("application/json")
                    )(
                      r =>
                        r.id.fold(
                          InternalServerError("An unexpected error occured!")
                            .as("application/json")
                        )(id => Ok(JsNumber(BigDecimal(id))))
                    )
                  else
                    Forbidden("You are not authorised to access this resource!").as(
                      "application/json"
                    )
              }
            }
        }
  }

  /**
    * List all dfasdl resources that are accessible by the current user.
    *
    * @return A page displaying the dfasdl resources.
    */
  def index: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          dfasdlResourceDAO.allReadable(uid, user.groupIds)(loadDfasdls = true).map { rs =>
            render {
              case Accepts.Html() => Ok(views.html.dashboard.dfasdlresources.index(rs))
              case Accepts.Json() =>
                Ok(Map("dfasdlresources" -> rs.toList).asJson.nospaces).as("application/json")
            }
          }
      }
  }

  /**
    * Display the detail page for a dfasdl resource with the given ID.
    *
    * @param id The database ID of the dfasdl resource.
    * @return The detail page or an error.
    */
  def show(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id
        .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
          uid =>
            for {
              ro <- dfasdlResourceDAO.findById(id)(loadDfasdl = true)
              auth <- ro.fold(Future.successful(false))(
                r => authorize(user, r.getReadAuthorisation)
              )
              vs <- ro.fold(Future.successful(Seq.empty[String]))(
                r => dfasdlResourceDAO.loadDfasdlVersions(r.dfasdl.id)
              ) if auth
            } yield {
              ro.fold(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              ) { r =>
                if (auth) {
                  val dataElementsPattern = dataElements.mkString("(?iu)<(", "|", ")").r
                  val des =
                    dataElementsPattern
                      .findAllMatchIn(r.dfasdl.content)
                      .map(m => m.group(1))
                      .toVector
                  val dataStats = des
                    .groupBy(e => e)
                    .mapValues(_.size)
                    .map(e => ChartDataEntry(label = e._1, value = e._2))
                    .toVector
                  val structElementsPattern = structElements.mkString("(?iu)<(", "|", ")").r
                  val ses = structElementsPattern
                    .findAllMatchIn(r.dfasdl.content)
                    .map(m => m.group(1))
                    .toVector
                  val structStats = ses
                    .groupBy(e => e)
                    .mapValues(_.size)
                    .map(e => ChartDataEntry(label = e._1, value = e._2))
                    .toVector
                  val dfasdlStats = DfasdlStatistics(
                    dataElements = dataStats,
                    structureElements = structStats
                  )
                  Ok(
                    views.html.dashboard.dfasdlresources
                      .show(r, vs.filterNot(v => v == r.dfasdl.version), dfasdlStats)
                  )
                } else
                  Forbidden(views.html.errors.forbidden())
              }
            }
        }
  }

  /**
    * Try to update the dfasdl resource with the given ID using the submitted form data.
    *
    * @param id The ID of the dfasdl resource.
    * @return Redirect to the detail page or display an error.
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
            val getGroups   = authDAO.allGroups
            val getUsers    = authDAO.allUsernames
            val getResource = dfasdlResourceDAO.findById(id)(loadDfasdl = false)
            for {
              ro <- getResource
              as <- getUsers if ro.isDefined
              gs <- getGroups if ro.isDefined
              auth <- ro.fold(Future.successful(false))(
                r => authorize(user, r.getWriteAuthorisation)
              )
              result <- ro.fold(
                Future.successful(
                  NotFound(
                    views.html.errors.notFound(Messages("errors.notfound.title"),
                                               Option(Messages("errors.notfound.header")))
                  )
                )
              )(
                r =>
                  DfasdlResourceForm.form
                    .bindFromRequest()
                    .fold(
                      formWithErrors =>
                        Future.successful(
                          BadRequest(
                            views.html.dashboard.dfasdlresources.edit(id, formWithErrors, gs, as)
                          )
                      ),
                      formData => {
                        val dfasdlRes = DFASDLResource(
                          id = Option(id),
                          dfasdl = formData.dfasdl,
                          ownerId = formData.authorisation.ownerId,
                          groupId = formData.authorisation.groupId,
                          groupPermissions = formData.authorisation.groupPermissions,
                          worldPermissions = formData.authorisation.worldPermissions
                        )
                        val validationResult: Try[scala.collection.immutable.Set[String]] = for {
                          b <- Try(createDocumentBuilder())
                          d <- Try(
                            b.parse(
                              new ByteArrayInputStream(
                                dfasdlRes.dfasdl.content.getBytes(StandardCharsets.UTF_8)
                              )
                            )
                          )
                          ids <- DFASDLResourcesController.calculateReservedSQLKeywords(d)
                        } yield ids
                        val validationError: String = validationResult match {
                          case scala.util.Failure(error) =>
                            Messages("validate.dfasdl.on-save.error", error.getMessage.trim)
                          case scala.util.Success(reservedNames) =>
                            if (reservedNames.nonEmpty)
                              Messages("validate.dfasdl.reservedNames",
                                       reservedNames.mkString(", "))
                            else
                              ""
                        }
                        dfasdlResourceDAO.update(dfasdlRes).map { f =>
                          if (f > 0)
                            Redirect(routes.DFASDLResourcesController.show(id))
                              .flashing(
                                "success" -> "Resource updated.",
                                "warning" -> validationError
                              )
                          else
                            Redirect(routes.DFASDLResourcesController.index())
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

  /**
    * Analyse the given connection information and try to generate a DFASDL that describes
    * the data's schema.
    *
    * @param connectionInformationId The database id of the `ConnectionInformationResource` that provides the connection information.
    * @return A `Future[Result]` with the dfasdl editor form holding the generated dfasdl upon success.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def generateDfasdl(connectionInformationId: Long): Action[AnyContent] =
    AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(
        Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
      ) { uid =>
        val getGroups = authDAO.allGroups
        val getUsers  = authDAO.allUsernames
        for {
          co   <- connectionInformationResourceDAO.findById(connectionInformationId)
          auth <- co.fold(Future.successful(false))(c => authorize(user, c.getReadAuthorisation))
          as   <- getUsers if auth
          gs   <- getGroups if auth
          result <- co.fold(
            Future.successful(
              NotFound(
                views.html.errors.notFound(Messages("errors.notfound.title"),
                                           Option(Messages("errors.notfound.header")))
              )
            )
          )(
            c =>
              (frontendSelection ? FrontendServiceMessages
                .ExtractSchema(c.connection, ExtractSchemaOptions.createDatabaseOptions())).map {
                case GlobalMessages.ExtractSchemaResult(s, r) =>
                  log.debug("Received dfasdl schema extraction result for {}.", s.uri)
                  r match {
                    case -\/(failure) =>
                      log.error("DFASDL schema extraction for {} failed! {}", s.uri, failure, "") // The last parameter is needed to avoid an "ambiguous reference to overloaded definition" error!
                      Redirect(routes.DFASDLResourcesController.index())
                        .flashing("error" -> failure)
                    case \/-(success) =>
                      val authorisation = ResourceAuthorisationFieldsForm.Data(
                        ownerId = uid,
                        groupId = user.groupIds.headOption,
                        groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
                        worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
                      )
                      val formData = DfasdlResourceForm.Data(
                        dfasdl = success,
                        authorisation = authorisation
                      )
                      Ok(
                        views.html.dashboard.dfasdlresources
                          .add(DfasdlResourceForm.form.fill(formData), gs, as)
                      )
                  }
                case msg =>
                  log.error(
                    "Received an unexpected message while waiting for dfasdl schema extraction! {}",
                    msg
                  )
                  Redirect(routes.DFASDLResourcesController.index())
                    .flashing("error" -> "An unexpected error occured!")
            }
          ) if auth
        } yield {
          if (auth) {
            co.fold(
              NotFound(
                views.html.errors.notFound(Messages("errors.notfound.title"),
                                           Option(Messages("errors.notfound.header")))
              )
            )(
              c => result
            )
          } else
            Forbidden(views.html.errors.forbidden())
        }
      }
    }

  /**
    * Display the form for configuring a DFASDL extraction from a CSV file.
    *
    * @param connectionInformationId The database id of the `ConnectionInformationResource` that provides the connection information.
    * @return The form for configuring the DFASDL extraction.
    */
  def configureDfasdlFromCsv(connectionInformationId: Long): Action[AnyContent] =
    AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
      val form = ExtractDfasdlForm.form.fill(
        ExtractDfasdlForm.Data(
          encoding = ExtractDfasdlForm.DEFAULT_ENCODING,
          header = false,
          separator = ExtractDfasdlForm.DEFAULT_SEPARATOR
        )
      )
      Future.successful(
        Ok(
          views.html.dashboard.connectioninformations
            .configureDfasdlFromCsv(connectionInformationId, form)
        )
      )
    }

  /**
    * Analyse the given connection information and try to generate a DFASDL that describes
    * the data's schema.
    *
    * @param connectionInformationId The database id of the `ConnectionInformationResource` that provides the connection information.
    * @return A `Future[Result]` with the dfasdl editor form holding the generated dfasdl upon success.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def generateDfasdlFromCSV(connectionInformationId: Long): Action[AnyContent] =
    AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token =>
          val user = loggedIn
          user.id.fold(
            Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
          ) { uid =>
            ExtractDfasdlForm.form
              .bindFromRequest()
              .fold(
                formWithError =>
                  Future.successful(
                    BadRequest(
                      views.html.dashboard.connectioninformations
                        .configureDfasdlFromCsv(connectionInformationId, formWithError)
                    )
                ),
                formData => {
                  val options = ExtractSchemaOptions.createCsvOptions(
                    hasHeaderLine = formData.header,
                    separator = formData.separator,
                    encoding = formData.encoding
                  )
                  val getGroups = authDAO.allGroups
                  val getUsers  = authDAO.allUsernames
                  for {
                    co <- connectionInformationResourceDAO.findById(connectionInformationId)
                    auth <- co
                      .fold(Future.successful(false))(c => authorize(user, c.getReadAuthorisation))
                    as <- getUsers if auth
                    gs <- getGroups if auth
                    result <- co.fold(
                      Future.successful(
                        NotFound(
                          views.html.errors.notFound(Messages("errors.notfound.title"),
                                                     Option(Messages("errors.notfound.header")))
                        )
                      )
                    )(
                      c =>
                        (frontendSelection ? FrontendServiceMessages.ExtractSchema(c.connection,
                                                                                   options)).map {
                          case GlobalMessages.ExtractSchemaResult(s, r) =>
                            log.debug("Received dfasdl schema extraction result for {}.", s.uri)
                            r match {
                              case -\/(failure) =>
                                log.error(
                                  "DFASDL schema extraction for {} failed! {}",
                                  s.uri,
                                  failure,
                                  ""
                                ) // The last parameter is needed to avoid an "ambiguous reference to overloaded definition" error!
                                Redirect(routes.DFASDLResourcesController.index())
                                  .flashing("error" -> failure)
                              case \/-(success) =>
                                val authorisation = ResourceAuthorisationFieldsForm.Data(
                                  ownerId = uid,
                                  groupId = user.groupIds.headOption,
                                  groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
                                  worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
                                )
                                val formData = DfasdlResourceForm.Data(
                                  dfasdl = success,
                                  authorisation = authorisation
                                )
                                Ok(
                                  views.html.dashboard.dfasdlresources
                                    .add(DfasdlResourceForm.form.fill(formData), gs, as)
                                )
                            }
                          case msg =>
                            log.error(
                              "Received an unexpected message while waiting for dfasdl schema extraction! {}",
                              msg
                            )
                            Redirect(routes.DFASDLResourcesController.index())
                              .flashing("error" -> "An unexpected error occured!")
                      }
                    ) if auth
                  } yield {
                    if (auth) {
                      co.fold(
                        NotFound(
                          views.html.errors.notFound(Messages("errors.notfound.title"),
                                                     Option(Messages("errors.notfound.header")))
                        )
                      )(
                        c => result
                      )
                    } else
                      Forbidden(views.html.errors.forbidden())
                  }
                }
              )
          }
      }
    }

  /**
    * Validate the DFASDL file that is submitted via the request body.
    *
    * @return A json containing error messages.
    */
  def validateDFASDL: Action[AnyContent] = StackAction(AuthorityKey -> UserAuthority) {
    implicit request =>
      request.body.asXml match {
        case None =>
          log.error("DFASDL validation missing request body!")
          BadRequest(
            Map(
              "message" -> Messages("validate.dfasdl.missingBody"),
              "class"   -> "text-danger",
              "error"   -> Messages("validate.dfasdl.missingBody")
            ).asJson.nospaces
          ).as("application/json")
        case Some(xml) =>
          val doc: Try[scala.collection.immutable.Set[String]] = for {
            b <- Try(createDocumentBuilder())
            d <- Try(
              b.parse(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)))
            )
            ids <- DFASDLResourcesController.calculateReservedSQLKeywords(d)
          } yield ids
          doc match {
            case scala.util.Failure(e) =>
              log.error("DFASDL validation failed!", e)
              BadRequest(
                Map(
                  "message" -> Messages("validate.dfasdl.error"),
                  "class"   -> "text-danger",
                  "error"   -> e.getMessage.trim
                ).asJson.nospaces
              ).as("application/json")
            case scala.util.Success(reservedNames) =>
              if (reservedNames.nonEmpty)
                Ok(
                  Map(
                    "message" -> Messages("validate.dfasdl.warning"),
                    "class"   -> "text-warning",
                    "error" -> Messages("validate.dfasdl.reservedNames",
                                        reservedNames.mkString(", "))
                  ).asJson.nospaces
                ).as("application/json")
              else
                Ok(
                  Map(
                    "message" -> Messages("validate.dfasdl.successful"),
                    "class"   -> "text-success",
                    "error"   -> ""
                  ).asJson.nospaces
                ).as("application/json")
          }
      }
  }

  /**
    * Retrieve all existing version numbers of the given dfasdl resource and return them.
    *
    * @param id The ID of the resource.
    * @return A list of version numbers.
    */
  def showVersions(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id
        .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
          uid =>
            for {
              ro <- dfasdlResourceDAO.findById(id)(loadDfasdl = true)
              auth <- ro.fold(Future.successful(false))(
                r => authorize(user, r.getReadAuthorisation)
              )
              vs <- ro.fold(Future.successful(Seq.empty[String]))(
                r => dfasdlResourceDAO.loadDfasdlVersions(r.dfasdl.id)
              ) if auth
            } yield {
              val versions = if (auth) vs else Seq.empty[String]
              render {
                case Accepts.Html() =>
                  if (auth)
                    ro.fold(NotFound(""))(r => Ok(versions.mkString(",")))
                  else
                    Forbidden(views.html.errors.forbidden())
                case Accepts.Json() =>
                  if (auth)
                    ro.fold(NotFound("").as("application/json"))(
                      r => Ok(versions.toList.asJson.nospaces).as("application/json")
                    )
                  else
                    Forbidden("").as("application/json")
              }
            }
        }
  }

  /**
    * Load the specified version of the given resource from the database.
    *
    * @param id      The ID of the resource.
    * @param version The version of the DFASDL to load.
    * @return The desired DFASDL version.
    */
  def loadDfasdlVersion(id: Long, version: String): Action[AnyContent] =
    AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(
        Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
      ) { uid =>
        for {
          ro   <- dfasdlResourceDAO.findById(id)(loadDfasdl = false)
          auth <- ro.fold(Future.successful(false))(r => authorize(user, r.getReadAuthorisation))
          vo <- ro.fold(Future.successful(None: Option[DFASDL]))(
            r => dfasdlResourceDAO.loadDfasdlVersion(r.dfasdl.id, version)
          ) if auth
        } yield {
          render {
            case Accepts.Html() =>
              if (auth)
                vo.fold(NotFound(""))(r => Ok(r.toString))
              else
                Forbidden(views.html.errors.forbidden())
            case Accepts.Json() =>
              if (auth)
                vo.fold(NotFound("").as("application/json"))(
                  r => Ok(r.asJson.nospaces).as("application/json")
                )
              else
                Forbidden("").as("application/json")
          }
        }
      }
    }

  /**
    * Show a site that compares two DFASDL versions and highlights the difference between the versions.
    *
    * @param id             The ID of the DFASDL Resource.
    * @param compareVersion The version that will be compared to the most actual version of the DFASDL.
    * @return The answer to the request
    */
  def diffVersions(id: Long, compareVersion: String): Action[AnyContent] =
    AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(
        Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
      ) { uid =>
        for {
          ro   <- dfasdlResourceDAO.findById(id)(loadDfasdl = true)
          auth <- ro.fold(Future.successful(false))(r => authorize(user, r.getReadAuthorisation))
          vo <- ro.fold(Future.successful(None: Option[DFASDL]))(
            r => dfasdlResourceDAO.loadDfasdlVersion(r.dfasdl.id, compareVersion)
          ) if auth
          diffLines <- ro.fold(Future.successful(List.empty[Int]))(
            r => Future.successful(determineDiffLines(r.dfasdl, vo))
          ) if auth
          vs <- ro.fold(Future.successful(Seq.empty[String]))(
            r => dfasdlResourceDAO.loadDfasdlVersions(r.dfasdl.id)
          ) if auth
        } yield {
          if (auth)
            ro.fold(
              NotFound(
                views.html.errors.notFound(Messages("errors.notfound.title"),
                                           Option(Messages("errors.notfound.header")))
              )
            )(
              r =>
                Ok(views.html.dashboard.dfasdlresources.diff(r, vo, diffLines, compareVersion, vs))
            )
          else
            Forbidden(views.html.errors.forbidden())
        }
      }
    }

  /**
    * Determine all lines that differ between the two DFASDL versions.
    *
    * @param actualDfasdl   The actual version of the DFASDL.
    * @param comparedDfasdl The version that is compared to the actualDfasdl version.
    * @return A list of line numbers that differ in these two versions.
    */
  def determineDiffLines(actualDfasdl: DFASDL, comparedDfasdl: Option[DFASDL]): List[Int] =
    comparedDfasdl.fold(List.empty[Int]) { cmpDfasdl =>
      if (actualDfasdl.version == cmpDfasdl.version)
        List.empty[Int]
      else {
        val linesA    = scala.io.Source.fromString(actualDfasdl.content).getLines().toList
        val linesB    = scala.io.Source.fromString(cmpDfasdl.content).getLines().toList
        val diffLines = linesA.zipWithIndex diff linesB.toArray.zipWithIndex
        diffLines.map(e => e._2)
      }
    }
}

object DFASDLResourcesController extends ElementHelpers {
  final val DfasdlContentPlaceholder: String =
    """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<dfasdl xmlns="http://www.dfasdl.org/DFASDL" semantic="custom">
      |
      |</dfasdl>""".stripMargin

  // A list of keywords that are reserved in ansi sql or databases.
  final val RESERVED_DB_KEYWORDS: Set[String] = Set(
    "ABS",
    "ACCESS",
    "ACCESSIBLE",
    "ACCOUNT",
    "ACTION",
    "ADD",
    "AFTER",
    "AGAINST",
    "AGGREGATE",
    "ALGORITHM",
    "ALL",
    "ALLOCATE",
    "ALTER",
    "ALWAYS",
    "ANALYSE",
    "ANALYZE",
    "AND",
    "ANY",
    "ARE",
    "ARRAY",
    "ARRAY_AGG",
    "ARRAY_MAX_CARDINALITY",
    "AS",
    "ASC",
    "ASCII",
    "ASENSITIVE",
    "ASYMMETRIC",
    "AT",
    "ATOMIC",
    "AUDIT",
    "AUTHORIZATION",
    "AUTO_INCREMENT",
    "AUTOEXTEND_SIZE",
    "AVG",
    "AVG_ROW_LENGTH",
    "BACKUP",
    "BEFORE",
    "BEGIN",
    "BEGIN_FRAME",
    "BEGIN_PARTITION",
    "BETWEEN",
    "BIGINT",
    "BINARY",
    "BINLOG",
    "BIT",
    "BIT_LENGTH",
    "BLOB",
    "BLOCK",
    "BOOL",
    "BOOLEAN",
    "BOTH",
    "BREAK",
    "BROWSE",
    "BTREE",
    "BULK",
    "BY",
    "BYTE",
    "CACHE",
    "CALL",
    "CARDINALITY",
    "CASCADE",
    "CASCADED",
    "CASE",
    "CAST",
    "CATALOG_NAME",
    "CEIL",
    "CEILING",
    "CHAIN",
    "CHANGE",
    "CHANGED",
    "CHANNEL",
    "CHAR",
    "CHAR_LENGTH",
    "CHARACTER",
    "CHARACTER_LENGTH",
    "CHARSET",
    "CHECK",
    "CHECKPOINT",
    "CHECKSUM",
    "CIPHER",
    "CLASS_ORIGIN",
    "CLIENT",
    "CLOB",
    "CLOSE",
    "CLUSTER",
    "CLUSTERED",
    "COALESCE",
    "CODE",
    "COLLATE",
    "COLLATION",
    "COLLECT",
    "COLUMN",
    "COLUMN_FORMAT",
    "COLUMN_NAME",
    "COLUMNS",
    "COMMENT",
    "COMMIT",
    "COMMITTED",
    "COMPACT",
    "COMPLETION",
    "COMPRESS",
    "COMPRESSED",
    "COMPRESSION",
    "COMPUTE",
    "CONCURRENT",
    "CONCURRENTLY",
    "CONDITION",
    "CONNECT",
    "CONNECTION",
    "CONSISTENT",
    "CONSTRAINT",
    "CONSTRAINT_CATALOG",
    "CONSTRAINT_NAME",
    "CONSTRAINT_SCHEMA",
    "CONTAINS",
    "CONTAINSTABLE",
    "CONTEXT",
    "CONTINUE",
    "CONVERT",
    "CORR",
    "CORRESPONDING",
    "COUNT",
    "COVAR_POP",
    "COVAR_SAMP",
    "CPU",
    "CREATE",
    "CROSS",
    "CUBE",
    "CUME_DIST",
    "CURRENT",
    "CURRENT_CATALOG",
    "CURRENT_DATE",
    "CURRENT_DEFAULT_TRANSFORM_GROUP",
    "CURRENT_PATH",
    "CURRENT_ROLE",
    "CURRENT_ROW",
    "CURRENT_SCHEMA",
    "CURRENT_TIME",
    "CURRENT_TIMESTAMP",
    "CURRENT_TRANSFORM_GROUP_FOR_TYPE",
    "CURRENT_USER",
    "CURSOR",
    "CURSOR_NAME",
    "DATA",
    "DATABASE",
    "DATABASES",
    "DATAFILE",
    "DATALINK",
    "DATE",
    "DATETIME",
    "DAY",
    "DAY_HOUR",
    "DAY_MICROSECOND",
    "DAY_MINUTE",
    "DAY_SECOND",
    "DBCC",
    "DEALLOCATE",
    "DEC",
    "DECIMAL",
    "DECLARE",
    "DEFAULT",
    "DEFAULT_AUTH",
    "DEFINER",
    "DELAY_KEY_WRITE",
    "DELAYED",
    "DELETE",
    "DENSE_RANK",
    "DENY",
    "DEREF",
    "DES_KEY_FILE",
    "DESC",
    "DESCRIBE",
    "DETERMINISTIC",
    "DIAGNOSTICS",
    "DIRECTORY",
    "DISABLE",
    "DISCARD",
    "DISCONNECT",
    "DISK",
    "DISTINCT",
    "DISTINCTROW",
    "DISTRIBUTED",
    "DIV",
    "DLNEWCOPY",
    "DLPREVIOUSCOPY",
    "DLURLCOMPLETE",
    "DLURLCOMPLETEONLY",
    "DLURLCOMPLETEWRITE",
    "DLURLPATH",
    "DLURLPATHONLY",
    "DLURLPATHWRITE",
    "DLURLSCHEME",
    "DLURLSERVER",
    "DLVALUE",
    "DO",
    "DOUBLE",
    "DROP",
    "DUAL",
    "DUMP",
    "DUMPFILE",
    "DUPLICATE",
    "DYNAMIC",
    "EACH",
    "ELEMENT",
    "ELSE",
    "ELSEIF",
    "ENABLE",
    "ENCLOSED",
    "ENCRYPTION",
    "END",
    "END_FRAME",
    "END_PARTITION",
    "END-EXEC",
    "ENDS",
    "ENGINE",
    "ENGINES",
    "ENUM",
    "ERRLVL",
    "ERROR",
    "ERRORS",
    "ESCAPE",
    "ESCAPED",
    "EVENT",
    "EVENTS",
    "EVERY",
    "EXCEPT",
    "EXCEPTION",
    "EXCHANGE",
    "EXCLUSIVE",
    "EXEC",
    "EXECUTE",
    "EXISTS",
    "EXIT",
    "EXP",
    "EXPANSION",
    "EXPIRE",
    "EXPLAIN",
    "EXPORT",
    "EXTENDED",
    "EXTENT_SIZE",
    "EXTERNAL",
    "FALSE",
    "FAST",
    "FAULTS",
    "FETCH",
    "FIELDS",
    "FILE",
    "FILE_BLOCK_SIZE",
    "FILLFACTOR",
    "FILTER",
    "FIRST",
    "FIRST_VALUE",
    "FIXED",
    "FLOAT",
    "FLOAT4",
    "FLOAT8",
    "FLOOR",
    "FLUSH",
    "FOLLOWS",
    "FOR",
    "FORCE",
    "FOREIGN",
    "FORMAT",
    "FOUND",
    "FRAME_ROW",
    "FREE",
    "FREETEXT",
    "FREETEXTTABLE",
    "FREEZE",
    "FROM",
    "FULL",
    "FULLTEXT",
    "FUNCTION",
    "FUSION",
    "GENERAL",
    "GENERATED",
    "GEOMETRY",
    "GEOMETRYCOLLECTION",
    "GET",
    "GET_FORMAT",
    "GLOBAL",
    "GOTO",
    "GRANT",
    "GRANTS",
    "GROUP",
    "GROUP_REPLICATION",
    "GROUPS",
    "HANDLER",
    "HASH",
    "HAVING",
    "HELP",
    "HIGH_PRIORITY",
    "HOLDLOCK",
    "HOST",
    "HOSTS",
    "HOUR",
    "HOUR_MICROSECOND",
    "HOUR_MINUTE",
    "HOUR_SECOND",
    "IDENTIFIED",
    "IDENTITY",
    "IDENTITY_INSERT",
    "IDENTITYCOL",
    "IF",
    "IGNORE",
    "IGNORE_SERVER_IDS",
    "ILIKE",
    "IMMEDIATE",
    "IMPORT",
    "IN",
    "INCREMENT",
    "INDEX",
    "INDEXES",
    "INDICATOR",
    "INFILE",
    "INITIAL",
    "INITIAL_SIZE",
    "INNER",
    "INOUT",
    "INSENSITIVE",
    "INSERT",
    "INSERT_METHOD",
    "INSTALL",
    "INSTANCE",
    "INT",
    "INT1",
    "INT2",
    "INT3",
    "INT4",
    "INT8",
    "INTEGER",
    "INTERSECT",
    "INTERSECTION",
    "INTERVAL",
    "INTO",
    "INVOKER",
    "IO",
    "IO_AFTER_GTIDS",
    "IO_BEFORE_GTIDS",
    "IO_THREAD",
    "IPC",
    "IS",
    "ISNULL",
    "ISOLATION",
    "ISSUER",
    "ITERATE",
    "JOIN",
    "JSON",
    "KEY",
    "KEY_BLOCK_SIZE",
    "KEYS",
    "KILL",
    "LAG",
    "LANGUAGE",
    "LAST",
    "LAST_VALUE",
    "LATERAL",
    "LEAD",
    "LEADING",
    "LEAVE",
    "LEAVES",
    "LEFT",
    "LESS",
    "LEVEL",
    "LIKE",
    "LIKE_REGEX",
    "LIMIT",
    "LINEAR",
    "LINENO",
    "LINES",
    "LINESTRING",
    "LIST",
    "LN",
    "LOAD",
    "LOCAL",
    "LOCALTIME",
    "LOCALTIMESTAMP",
    "LOCK",
    "LOCKS",
    "LOGFILE",
    "LOGS",
    "LONG",
    "LONGBLOB",
    "LONGTEXT",
    "LOOP",
    "LOW_PRIORITY",
    "LOWER",
    "MASTER",
    "MASTER_AUTO_POSITION",
    "MASTER_BIND",
    "MASTER_CONNECT_RETRY",
    "MASTER_DELAY",
    "MASTER_HEARTBEAT_PERIOD",
    "MASTER_HOST",
    "MASTER_LOG_FILE",
    "MASTER_LOG_POS",
    "MASTER_PASSWORD",
    "MASTER_PORT",
    "MASTER_RETRY_COUNT",
    "MASTER_SERVER_ID",
    "MASTER_SSL",
    "MASTER_SSL_CA",
    "MASTER_SSL_CAPATH",
    "MASTER_SSL_CERT",
    "MASTER_SSL_CIPHER",
    "MASTER_SSL_CRL",
    "MASTER_SSL_CRLPATH",
    "MASTER_SSL_KEY",
    "MASTER_SSL_VERIFY_SERVER_CERT",
    "MASTER_TLS_VERSION",
    "MASTER_USER",
    "MATCH",
    "MAX",
    "MAX_CARDINALITY",
    "MAX_CONNECTIONS_PER_HOUR",
    "MAX_QUERIES_PER_HOUR",
    "MAX_ROWS",
    "MAX_SIZE",
    "MAX_STATEMENT_TIME",
    "MAX_UPDATES_PER_HOUR",
    "MAX_USER_CONNECTIONS",
    "MAXEXTENTS",
    "MAXVALUE",
    "MEDIUM",
    "MEDIUMBLOB",
    "MEDIUMINT",
    "MEDIUMTEXT",
    "MEMBER",
    "MEMORY",
    "MERGE",
    "MESSAGE_TEXT",
    "MICROSECOND",
    "MIDDLEINT",
    "MIGRATE",
    "MIN",
    "MIN_ROWS",
    "MINUS",
    "MINUTE",
    "MINUTE_MICROSECOND",
    "MINUTE_SECOND",
    "MLSLABEL",
    "MOD",
    "MODE",
    "MODIFIES",
    "MODIFY",
    "MODULE",
    "MONTH",
    "MULTILINESTRING",
    "MULTIPOINT",
    "MULTIPOLYGON",
    "MULTISET",
    "MUTEX",
    "MYSQL_ERRNO",
    "NAME",
    "NAMES",
    "NATIONAL",
    "NATURAL",
    "NCHAR",
    "NCLOB",
    "NDB",
    "NDBCLUSTER",
    "NEVER",
    "NEW",
    "NEXT",
    "NO",
    "NO_WAIT",
    "NO_WRITE_TO_BINLOG",
    "NOAUDIT",
    "NOCHECK",
    "NOCOMPRESS",
    "NODEGROUP",
    "NONBLOCKING",
    "NONCLUSTERED",
    "NONE",
    "NORMALIZE",
    "NOT",
    "NOTNULL",
    "NOWAIT",
    "NTH_VALUE",
    "NTILE",
    "NULL",
    "NULLIF",
    "NUMBER",
    "NUMERIC",
    "NVARCHAR",
    "OCCURRENCES_REGEX",
    "OCTET_LENGTH",
    "OF",
    "OFF",
    "OFFLINE",
    "OFFSET",
    "OFFSETS",
    "OLD",
    "OLD_PASSWORD",
    "ON",
    "ONE",
    "ONLINE",
    "ONLY",
    "OPEN",
    "OPENDATASOURCE",
    "OPENQUERY",
    "OPENROWSET",
    "OPENXML",
    "OPTIMIZE",
    "OPTIMIZER_COSTS",
    "OPTION",
    "OPTIONALLY",
    "OPTIONS",
    "OR",
    "ORDER",
    "OUT",
    "OUTER",
    "OUTFILE",
    "OVER",
    "OVERLAPS",
    "OWNER",
    "PACK_KEYS",
    "PAGE",
    "PARAMETER",
    "PARSE_GCOL_EXPR",
    "PARSER",
    "PARTIAL",
    "PARTITION",
    "PARTITIONING",
    "PARTITIONS",
    "PASSWORD",
    "PCTFREE",
    "PERCENT",
    "PERCENT_RANK",
    "PERCENTILE_CONT",
    "PERCENTILE_DISC",
    "PERIOD",
    "PHASE",
    "PIVOT",
    "PLAN",
    "PLUGIN",
    "PLUGIN_DIR",
    "PLUGINS",
    "POINT",
    "POLYGON",
    "PORT",
    "PORTION",
    "POSITION_REGEX",
    "POWER",
    "PRECEDES",
    "PRECISION",
    "PREPARE",
    "PRESERVE",
    "PREV",
    "PRIMARY",
    "PRINT",
    "PRIOR",
    "PRIVILEGES",
    "PROC",
    "PROCEDURE",
    "PROCESSLIST",
    "PROFILE",
    "PROFILES",
    "PROXY",
    "PUBLIC",
    "PURGE",
    "QUARTER",
    "QUERY",
    "QUICK",
    "RAISERROR",
    "RANGE",
    "RANK",
    "RAW",
    "READ",
    "READ_ONLY",
    "READ_WRITE",
    "READS",
    "READTEXT",
    "REAL",
    "REBUILD",
    "RECONFIGURE",
    "RECOVER",
    "REDO_BUFFER_SIZE",
    "REDOFILE",
    "REDUNDANT",
    "REFERENCES",
    "REFERENCING",
    "REGEXP",
    "REGR_AVGX",
    "REGR_AVGY",
    "REGR_COUNT",
    "REGR_INTERCEPT",
    "REGR_R2",
    "REGR_SLOPE",
    "REGR_SXX",
    "REGR_SXY",
    "REGR_SYY",
    "RELAY",
    "RELAY_LOG_FILE",
    "RELAY_LOG_POS",
    "RELAY_THREAD",
    "RELAYLOG",
    "RELEASE",
    "RELOAD",
    "REMOVE",
    "RENAME",
    "REORGANIZE",
    "REPAIR",
    "REPEAT",
    "REPEATABLE",
    "REPLACE",
    "REPLICATE_DO_DB",
    "REPLICATE_DO_TABLE",
    "REPLICATE_IGNORE_DB",
    "REPLICATE_IGNORE_TABLE",
    "REPLICATE_REWRITE_DB",
    "REPLICATE_WILD_DO_TABLE",
    "REPLICATE_WILD_IGNORE_TABLE",
    "REPLICATION",
    "REQUIRE",
    "RESET",
    "RESIGNAL",
    "RESOURCE",
    "RESTORE",
    "RESTRICT",
    "RESULT",
    "RESUME",
    "RETURN",
    "RETURNED_SQLSTATE",
    "RETURNS",
    "REVERSE",
    "REVERT",
    "REVOKE",
    "RIGHT",
    "RLIKE",
    "ROLLBACK",
    "ROLLUP",
    "ROTATE",
    "ROUTINE",
    "ROW",
    "ROW_COUNT",
    "ROW_FORMAT",
    "ROW_NUMBER",
    "ROWCOUNT",
    "ROWGUIDCOL",
    "ROWID",
    "ROWNUM",
    "ROWS",
    "RTREE",
    "RULE",
    "SAVE",
    "SAVEPOINT",
    "SCHEDULE",
    "SCHEMA",
    "SCHEMA_NAME",
    "SCHEMAS",
    "SCOPE",
    "SECOND",
    "SECOND_MICROSECOND",
    "SECURITY",
    "SECURITYAUDIT",
    "SELECT",
    "SEMANTICKEYPHRASETABLE",
    "SEMANTICSIMILARITYDETAILSTABLE",
    "SEMANTICSIMILARITYTABLE",
    "SENSITIVE",
    "SEPARATOR",
    "SERIAL",
    "SERIALIZABLE",
    "SERVER",
    "SESSION",
    "SESSION_USER",
    "SET",
    "SETUSER",
    "SHARE",
    "SHOW",
    "SHUTDOWN",
    "SIGNAL",
    "SIGNED",
    "SIMILAR",
    "SIMPLE",
    "SIZE",
    "SLAVE",
    "SLOW",
    "SMALLINT",
    "SNAPSHOT",
    "SOCKET",
    "SOME",
    "SONAME",
    "SOUNDS",
    "SOURCE",
    "SPATIAL",
    "SPECIFIC",
    "SPECIFICTYPE",
    "SQL",
    "SQL_AFTER_GTIDS",
    "SQL_AFTER_MTS_GAPS",
    "SQL_BEFORE_GTIDS",
    "SQL_BIG_RESULT",
    "SQL_BUFFER_RESULT",
    "SQL_CACHE",
    "SQL_CALC_FOUND_ROWS",
    "SQL_NO_CACHE",
    "SQL_SMALL_RESULT",
    "SQL_THREAD",
    "SQL_TSI_DAY",
    "SQL_TSI_HOUR",
    "SQL_TSI_MINUTE",
    "SQL_TSI_MONTH",
    "SQL_TSI_QUARTER",
    "SQL_TSI_SECOND",
    "SQL_TSI_WEEK",
    "SQL_TSI_YEAR",
    "SQLCODE",
    "SQLERROR",
    "SQLEXCEPTION",
    "SQLSTATE",
    "SQLWARNING",
    "SQRT",
    "SSL",
    "STACKED",
    "START",
    "STARTING",
    "STARTS",
    "STATIC",
    "STATISTICS",
    "STATS_AUTO_RECALC",
    "STATS_PERSISTENT",
    "STATS_SAMPLE_PAGES",
    "STATUS",
    "STDDEV_POP",
    "STDDEV_SAMP",
    "STOP",
    "STORAGE",
    "STORED",
    "STRAIGHT_JOIN",
    "STRING",
    "SUBCLASS_ORIGIN",
    "SUBJECT",
    "SUBMULTISET",
    "SUBPARTITION",
    "SUBPARTITIONS",
    "SUBSTRING_REGEX",
    "SUCCEEDS",
    "SUCCESSFUL",
    "SUM",
    "SUPER",
    "SUSPEND",
    "SWAPS",
    "SWITCHES",
    "SYMMETRIC",
    "SYNONYM",
    "SYSDATE",
    "SYSTEM_TIME",
    "SYSTEM_USER",
    "TABLE",
    "TABLE_CHECKSUM",
    "TABLE_NAME",
    "TABLES",
    "TABLESAMPLE",
    "TABLESPACE",
    "TEMPORARY",
    "TEMPTABLE",
    "TERMINATED",
    "TEXT",
    "TEXTSIZE",
    "THAN",
    "THEN",
    "TIME",
    "TIMESTAMP",
    "TIMESTAMPADD",
    "TIMESTAMPDIFF",
    "TIMEZONE_HOUR",
    "TIMEZONE_MINUTE",
    "TINYBLOB",
    "TINYINT",
    "TINYTEXT",
    "TO",
    "TOP",
    "TRAILING",
    "TRAN",
    "TRANSACTION",
    "TRANSLATE",
    "TRANSLATE_REGEX",
    "TRANSLATION",
    "TRIGGER",
    "TRIGGERS",
    "TRIM_ARRAY",
    "TRUE",
    "TRUNCATE",
    "TRY_CONVERT",
    "TSEQUAL",
    "TYPE",
    "TYPES",
    "UESCAPE",
    "UID",
    "UNCOMMITTED",
    "UNDEFINED",
    "UNDO",
    "UNDO_BUFFER_SIZE",
    "UNDOFILE",
    "UNICODE",
    "UNINSTALL",
    "UNION",
    "UNIQUE",
    "UNKNOWN",
    "UNLOCK",
    "UNNEST",
    "UNPIVOT",
    "UNSIGNED",
    "UNTIL",
    "UPDATE",
    "UPDATETEXT",
    "UPGRADE",
    "UPPER",
    "USAGE",
    "USE",
    "USE_FRM",
    "USER",
    "USER_RESOURCES",
    "USING",
    "UTC_DATE",
    "UTC_TIME",
    "UTC_TIMESTAMP",
    "VALIDATE",
    "VALIDATION",
    "VALUE",
    "VALUE_OF",
    "VALUES",
    "VAR_POP",
    "VAR_SAMP",
    "VARBINARY",
    "VARCHAR",
    "VARCHAR2",
    "VARCHARACTER",
    "VARIABLES",
    "VARIADIC",
    "VARYING",
    "VERBOSE",
    "VERSIONING",
    "VIEW",
    "VIRTUAL",
    "WAIT",
    "WAITFOR",
    "WARNINGS",
    "WEEK",
    "WEIGHT_STRING",
    "WHEN",
    "WHENEVER",
    "WHERE",
    "WHILE",
    "WIDTH_BUCKET",
    "WINDOW",
    "WITH",
    "WITHINGROUP",
    "WITHOUT",
    "WORK",
    "WRAPPER",
    "WRITE",
    "WRITETEXT",
    "X509",
    "XA",
    "XID",
    "XML",
    "XMLAGG",
    "XMLBINARY",
    "XMLCAST",
    "XMLCOMMENT",
    "XMLDOCUMENT",
    "XMLITERATE",
    "XMLNAMESPACES",
    "XMLQUERY",
    "XMLTABLE",
    "XMLTEXT",
    "XMLVALIDATE",
    "XOR",
    "YEAR",
    "YEAR_MONTH",
    "ZEROFILL"
  )

  /**
    * Traverse all elements of the given XML document and return a list of
    * reserved names that are used either in the `id` or the `db-column-name`
    * attributes.
    *
    * @param doc An XML document containing a DFASDL.
    * @return Either an error or a set of reserved SQL keywords used in the document.
    */
  @SuppressWarnings(
    Array("org.wartremover.warts.AsInstanceOf",
          "org.wartremover.warts.Null",
          "org.wartremover.warts.Var")
  )
  def calculateReservedSQLKeywords(doc: Document): Try[Set[String]] = Try {
    val t: DocumentTraversal = doc.asInstanceOf[DocumentTraversal]
    val w: NodeIterator =
      t.createNodeIterator(doc.getDocumentElement, NodeFilter.SHOW_ELEMENT, null, true)

    var es: Set[String]            = Set.empty[String]
    var nextNode: org.w3c.dom.Node = w.nextNode()
    while (nextNode != null) {
      val e: Element = nextNode.asInstanceOf[Element]
      // This check is only relevant for data elements which map to columns and sequence elements which map to tables.
      if (isDataElement(e.getTagName) || e.getTagName == ElementNames.SEQUENCE || e.getTagName == ElementNames.FIXED_SEQUENCE) {
        val id: String = Try(e.getAttribute("id").toUpperCase(Locale.ROOT)).getOrElse("")
        val cn: String =
          Try(e.getAttribute(AttributeNames.DB_COLUMN_NAME).toUpperCase(Locale.ROOT)).getOrElse("")
        if (id.nonEmpty && RESERVED_DB_KEYWORDS.contains(id)) {
          es = es + id
        } else if (cn.nonEmpty && RESERVED_DB_KEYWORDS.contains(cn)) {
          es = es + cn
        }
      }
      nextNode = w.nextNode()
    }
    es
  }
}
