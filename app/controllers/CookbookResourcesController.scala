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

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Files
import java.util.zip.GZIPInputStream

import actors.FrontendService
import actors.FrontendService.FrontendServiceMessages
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.scaladsl.Compression
import akka.util.{ByteString, Timeout}
import argonaut.Argonaut._
import argonaut._
import com.google.inject.Inject
import com.wegtam.tensei.adt.{Cookbook, DFASDL, DFASDLReference}
import com.wegtam.tensei.server.suggesters.{MappingSuggesterMessages, MappingSuggesterModes}
import dao.{AuthDAO, CookbookResourceDAO, DFASDLResourceDAO, TransformationConfigurationDAO}
import forms.{CookbookResourceForm, ResourceAuthorisationFieldsForm}
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.UserAuthority
import models.{CookbookResource, DFASDLResource, Permission}
import play.api.http.HttpEntity
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.iteratee.Enumerator
import play.api.mvc._
import play.api.{Configuration, Logger}
import play.filters.csrf.CSRF

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scalaz.{Validation, _}

/**
  * The controller provides functionality to manage cookbook resources and backend functions
  * for the cookbook mapping editor.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param cookbookResourceDAO The DAO for handling cookbook resources provided via dependency injection.
  * @param dfasdlResourceDAO The DAO for handling DFASDL resources provided via dependency injection.
  * @param transformationConfigurationDAO The DAO for managing transformation configurations provided via dependency injection.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class CookbookResourcesController @Inject() (
    protected val configuration:    Configuration,
    val messagesApi:                MessagesApi,
    authDAO:                        AuthDAO,
    cookbookResourceDAO:            CookbookResourceDAO,
    dfasdlResourceDAO:              DFASDLResourceDAO,
    transformationConfigurationDAO: TransformationConfigurationDAO,
    implicit val system:            ActorSystem,
    implicit val webJarAssets:      WebJarAssets
) extends Controller with AuthElement with AuthConfigImpl with I18nSupport {
  private final val COOKBOOK_ENCODING: Charset = StandardCharsets.UTF_8

  private val log = Logger.logger

  private val DEFAULT_ASK_TIMEOUT = 5000L // The fallback default timeout for `ask` operations in milliseconds.

  private val frontendSelection = system.actorSelection(s"/user/${FrontendService.name}")
  implicit val timeout: Timeout = Timeout(FiniteDuration(configuration.getMilliseconds("tensei.frontend.ask-timeout").getOrElse(DEFAULT_ASK_TIMEOUT), MILLISECONDS))

  /**
    * A function that returns a `User` object from an `Id`.
    *
    * @todo Currently we override this method from the trait `AuthConfigImpl` because we can't use dependecy injection there to get the appropriate DAO.
    * @param id  The unique database id e.g. primary key for the user.
    * @param context The execution context.
    * @return An option to the user wrapped into a future.
    */
  override def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]] = authDAO.findAccountById(id)

  /**
    * Display the form to create a cookbook resource.
    *
    * @return The form to create a cookbook resource or an error page.
    */
  def add: Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          val user = loggedIn
          val getUsers = authDAO.allUsernames
          val getGroups = authDAO.allGroups
          for {
            as ← getUsers
            gs ← getGroups
          } yield {
            user.id.fold(InternalServerError(views.html.dashboard.errors.serverError())) {
              uid ⇒
                val authorisation = ResourceAuthorisationFieldsForm.Data(
                  ownerId = uid,
                  groupId = user.groupIds.headOption,
                  groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
                  worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
                )
                val formData = CookbookResourceForm.Data(
                  cookbookId = "",
                  authorisation = authorisation
                )
                Ok(views.html.dashboard.cookbookresources.add(CookbookResourceForm.form.fill(formData), gs, as))
            }
          }
      }
  }

  /**
    * Export the cookbook that is attached to the cookbook resource with the given ID.
    *
    * @param id The ID of a cookbook resource.
    * @return A download of the exported cookbook or an error page.
    */
  def exportCookbook(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      for {
        co ← cookbookResourceDAO.findById(id)(loadCookbook = true)
        auth ← co.fold(Future.successful(false))(c ⇒ authorize(user, c.getReadAuthorisation))
      } yield {
        if (auth) {
          co.fold(NotFound(views.html.errors.notFound(Messages("errors.notfound.title"), Option(Messages("errors.notfound.header"))))) {
            cr ⇒
              val content = cr.cookbook.asJson.nospaces
              val enum = Enumerator(ByteString.fromArray(content.getBytes(COOKBOOK_ENCODING)))
              val source: akka.stream.scaladsl.Source[ByteString, _] = akka.stream.scaladsl.Source.fromPublisher(play.api.libs.streams.Streams.enumeratorToPublisher(enum))
              Result(
                header = ResponseHeader(OK, Map(CONTENT_DISPOSITION → s"attachment; filename=${cr.cookbook.id}.json.gz")),
                body = HttpEntity.Streamed(source.via(Compression.gzip), None, None)
              )
          }
        }
        else
          Forbidden(views.html.errors.forbidden())
      }
  }

  /**
    * Display the form for importing a cookbook.
    *
    * @return The form for importing a cookbook.
    */
  def importCookbook: Action[AnyContent] = StackAction(AuthorityKey → UserAuthority) {
    implicit request ⇒
      CSRF.getToken.fold(Forbidden(views.html.errors.forbidden()))(
        implicit token ⇒
          Ok(views.html.dashboard.cookbookresources.importCB())
      )
  }

  /**
    * Try to import a cookbook using the submitted form data.
    *
    * @return Redirect to the detail page upon success or to the index or import page if an error occured.
    */
  def processImportCookbook: Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      final case class ImportError(message: String)

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          val user = loggedIn
          user.id.fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError())))(
            uid ⇒
              request.body.asMultipartFormData.fold(Future.successful(Redirect(routes.CookbookResourcesController.importCookbook()).flashing("error" → "No multipart form data found in request body!")))(
                body ⇒ body.file("cookbookFile").fold(Future.successful(Redirect(routes.CookbookResourcesController.importCookbook()).flashing("error" → "No cookbook file found in form data!"))) {
                  tempFile ⇒
                    Try(scala.io.Source.fromInputStream(new GZIPInputStream(Files.newInputStream(tempFile.ref.file.toPath)), COOKBOOK_ENCODING.name()).mkString) match {
                      case scala.util.Failure(failure) ⇒
                        log.error("Unable to read input file!", failure)
                        val message = Try(failure.getMessage).toOption
                        Future.successful(Redirect(routes.CookbookResourcesController.importCookbook()).flashing("error" → s"Unable to read input file! ${message.getOrElse("")}"))
                      case scala.util.Success(json) ⇒
                        Parse.decodeEither[Cookbook](json) match {
                          case -\/(failure) ⇒
                            log.error("Unable to parse json from imported cookbook! {}", failure)
                            Future.successful(Redirect(routes.CookbookResourcesController.importCookbook()).flashing("error" → "Unable to parse json!"))
                          case \/-(cookbook) ⇒
                            log.debug("Parsed cookbook with id {} from imported json file.", cookbook.id)
                            val getCookbook = cookbookResourceDAO.findByCookbookId(cookbook.id)(loadCookbook = true)
                            val dfasdlIds = cookbook.target.fold(Set.empty[String])(t ⇒ Set(t.id)) ++ cookbook.sources.map(_.id).toSet
                            val dfasdls = cookbook.target.fold(Set.empty[DFASDL])(t ⇒ Set(t)) ++ cookbook.sources.toSet
                            val getDfasdls = dfasdlResourceDAO.findByDfasdlIds(dfasdlIds)
                            val forceImport = body.dataParts.getOrElse("forceImport", Seq.empty).nonEmpty
                            if (forceImport)
                              log.info("User {} is forcing the import of cookbook {}.", uid, cookbook.id)
                            else
                              log.info("User {} is import the cookbook {}.", uid, cookbook.id)

                            for {
                              co ← getCookbook
                              ds ← getDfasdls
                              errors ← Future.successful(
                                co.fold(Set.empty[ImportError])(c ⇒ Set(ImportError(s"Cookbook with id ${c.cookbook.id} already exists!"))) ++ dfasdlIds.flatMap(
                                  id ⇒
                                    ds.find(_.dfasdl.id == id).flatMap(
                                      existingDfasdlResource ⇒ cookbook.findDFASDL(DFASDLReference(cookbookId = cookbook.id, dfasdlId = id)).flatMap(
                                        importedDfasdl ⇒
                                          if (importedDfasdl.version == existingDfasdlResource.dfasdl.version)
                                            Option(ImportError(s"DFASDL with id $id already exists!"))
                                          else
                                            None
                                      )
                                    )
                                )
                              )
                              dsImport ← Future.sequence {
                                if (errors.isEmpty || forceImport)
                                  dfasdls.map(dfasdl ⇒ dfasdlResourceDAO.create(DFASDLResource(
                                    id = None,
                                    dfasdl = dfasdl,
                                    ownerId = uid,
                                    groupId = user.groupIds.headOption,
                                    groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
                                    worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
                                  )))
                                else
                                  Set.empty[Future[Try[DFASDLResource]]]
                              }
                              cbImport ← co.fold {
                                cookbookResourceDAO.create(CookbookResource(
                                  id = None,
                                  cookbook = cookbook,
                                  ownerId = uid,
                                  groupId = user.groupIds.headOption,
                                  groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
                                  worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
                                ))
                              } { c =>
                                if (forceImport)
                                  cookbookResourceDAO.update(c.copy(cookbook = cookbook))
                                else
                                  Future.successful(scala.util.Failure(new Error("Could not import cookbook!")))
                              }
                            } yield {
                              if (errors.nonEmpty && !forceImport)
                                Redirect(routes.CookbookResourcesController.importCookbook()).flashing("error" → errors.map(_.message).mkString(", "))
                              else
                                cbImport match {
                                  case scala.util.Failure(e) ⇒
                                    log.error("Could not import cookbook!", e)
                                    Redirect(routes.CookbookResourcesController.index()).flashing("error" → "Could not import cookbook!")
                                  case scala.util.Success(c) ⇒
                                    c.id.fold(Redirect(routes.CookbookResourcesController.index()).flashing("error" → "Could not import cookbook!"))(
                                      id ⇒ Redirect(routes.CookbookResourcesController.show(id)).flashing("success" → "Imported cookbook.")
                                    )
                                }
                            }
                        }
                    }
                }
              )
          )
      }
  }

  /**
    * Try to create a cookbook resource from the submitted form data.
    *
    * @return Either redirect to the resource detail page or display an error.
    */
  def create: Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          val getUsers = authDAO.allUsernames
          val getGroups = authDAO.allGroups
          for {
            as ← getUsers
            gs ← getGroups
            ro ← CookbookResourceForm.form.bindFromRequest().fold(
              formWithErrors ⇒ Future.successful(Failure(BadRequest(views.html.dashboard.cookbookresources.add(formWithErrors, gs, as)))),
              formData ⇒ {
                cookbookResourceDAO.findByCookbookId(formData.cookbookId)(loadCookbook = false).map(
                  co ⇒ co.fold(Success(formData): Validation[Result, CookbookResourceForm.Data])(c ⇒ Failure(BadRequest(views.html.dashboard.cookbookresources.add(CookbookResourceForm.form.fill(formData).withGlobalError("A cookbook with that name already exists!"), gs, as))))
                )
              }
            )
            result ← ro match {
              case Failure(f) ⇒ Future.successful(f)
              case Success(d) ⇒
                val r = CookbookResource(
                  id = None,
                  cookbook = Cookbook(
                    id = d.cookbookId,
                    sources = List.empty,
                    target = None,
                    recipes = List.empty
                  ),
                  ownerId = d.authorisation.ownerId,
                  groupId = d.authorisation.groupId,
                  groupPermissions = d.authorisation.groupPermissions,
                  worldPermissions = d.authorisation.worldPermissions
                )
                cookbookResourceDAO.create(r).map {
                  case scala.util.Failure(e) ⇒
                    log.error("Could not create cookbook resource!", e)
                    Redirect(routes.CookbookResourcesController.index()).flashing("error" → "Could not create resource!")
                  case scala.util.Success(c) ⇒
                    c.id.fold(Redirect(routes.CookbookResourcesController.index()).flashing("error" → "Could not create resource!"))(
                      id ⇒ Redirect(routes.CookbookResourcesController.edit(id).withFragment(s"/$id/resources/").toString).flashing("success" → "Created resource.")
                    )
                }
            }
          } yield result
      }
  }

  /**
    * Destroy the cookbook resource with the given id in the database.
    *
    * @param id The database ID of the cookbook resource.
    * @return Redirect to the index page flashing success or failure or display an error page.
    */
  def destroy(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          val user = loggedIn
          val getResource = cookbookResourceDAO.findById(id)(loadCookbook = false)
          val usedByTransformations = transformationConfigurationDAO.existsByCookbookResourceId(id)
          for {
            co ← getResource
            usedBy ← usedByTransformations
            auth ← co.fold(Future.successful(false))(c ⇒ authorize(user, c.getWriteAuthorisation))
            result ← if (auth && usedBy < 1) co.fold(Future.successful(0))(c ⇒ cookbookResourceDAO.destroy(c)) else Future.successful(0)
          } yield {
            if (auth)
              if (usedBy < 1)
                if (result > 0)
                  Redirect(routes.CookbookResourcesController.index()).flashing("success" → Messages("ui.model.deleted", Messages("models.cookbook")))
                else
                  Redirect(routes.CookbookResourcesController.index()).flashing("error" → Messages("errors.deleted.unsuccessful"))
              else
                Redirect(routes.CookbookResourcesController.index()).flashing("error" → Messages("errors.cookbookresource.delete.used", usedBy))
            else
              Forbidden(views.html.errors.forbidden())
          }
      }
  }

  /**
    * Open the cookbook mapping editor for the given cookbook resource ID.
    *
    * @param id The database ID of the cookbook resource that contains the cookbook.
    * @return The editor or an error page.
    */
  def edit(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      for {
        co ← cookbookResourceDAO.findById(id)(loadCookbook = false)
        auth ← co.fold(Future.successful(false))(c ⇒ authorize(user, c.getWriteAuthorisation))
      } yield {
        if (auth)
          co.fold(NotFound(views.html.errors.notFound(Messages("errors.notfound.title"), Option(Messages("errors.notfound.header")))))(
            _ ⇒ Ok(views.html.dashboard.cookbookresources.editor(id))
          )
        else
          Forbidden(views.html.errors.forbidden())
      }
  }

  /**
    * List all cookbook resources that the current user has access to.
    *
    * @return A list of all cookbook resources that are readable by the user.
    */
  def index: Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit rs ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError())))(
        uid ⇒ cookbookResourceDAO.allReadable(uid, user.groupIds)(loadCookbooks = false).map {
          cs ⇒
            render {
              case Accepts.Html() ⇒
                Ok(views.html.dashboard.cookbookresources.index(cs))
              case Accepts.Json() ⇒
                val json = Json.obj(
                  "cookbookresources" → cs.toList.asJson
                )
                Ok(json.nospaces).as("application/json")
            }
        }
      )
  }

  /**
    * Check if the given cookbook name (Which will be the unqiue cookbook ID in the later process.) already
    * exists on another cookbook resource.
    *
    * @param name The name of the cookbook attached to the resource with the given ID.
    * @param id The ID of the cookbook resource.
    * @return Either true or false.
    */
  def cookbookExists(name: String, id: Long): Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit rs ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val getCookbook = cookbookResourceDAO.findById(id)(loadCookbook = true)
      val getByName = cookbookResourceDAO.findByCookbookId(name)(loadCookbook = false)
      for {
        co ← getCookbook
        eo ← getByName
      } yield {
        val response: Boolean = eo.fold(false)(_ ⇒ co.fold(true)(_ ⇒ false))
        render {
          case Accepts.Html() ⇒ Ok(response.toString)
          case Accepts.Json() ⇒ Ok(response.asJson.nospaces).as("application/json")
        }
      }
  }

  /**
    * Show the detail page for the cookbook resource with the given id.
    *
    * @param id The database id of a cookbook resource.
    * @return The detail page or an error.
    */
  def show(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid ⇒
          for {
            co ← cookbookResourceDAO.findById(id)(loadCookbook = true)
            auth ← co.fold(Future.successful(false))(c ⇒ authorize(user, c.getReadAuthorisation))
            ds ← co.fold(Future.successful(Seq.empty[DFASDLResource])) {
              c ⇒
                val dfasdlIds = c.cookbook.target.fold(Set.empty[String])(t ⇒ Set(t.id)) ++ c.cookbook.sources.map(_.id).toSet
                dfasdlResourceDAO.findByDfasdlIds(dfasdlIds)
            } if auth
          } yield {
            render {
              case Accepts.Html() ⇒
                if (auth)
                  co.fold(NotFound(views.html.errors.notFound(Messages("errors.notfound.title"), Option(Messages("errors.notfound.header")))))(
                    c ⇒ Ok(views.html.dashboard.cookbookresources.show(c, ds.toSet))
                  )
                else
                  Forbidden(views.html.errors.forbidden())
              case Accepts.Json() ⇒
                if (auth)
                  co.fold(NotFound("The requested resource was not found!").as("application/json"))(
                    c ⇒ Ok(Json.obj("cookbookresource" → c.asJson).nospaces).as("application/json")
                  )
                else
                  Forbidden("You are not authorised to access this resource!").as("application/json")
            }
          }
      }
  }

  /**
    * Update the cookbook resource with the given id using the submitted request body.
    *
    * '''This action is intended to be called by the editor component with a request body containing the necessary json.'''
    *
    * @param id The database id of the cookbook resource.
    * @return An appropriate http status code and a message.
    */
  def update(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid ⇒
          val jsonBody = request.body.asJson.fold(None: Option[String])(j ⇒ (j \ "cookbookresource").toOption.map(_.toString()))
          for {
            co ← cookbookResourceDAO.findById(id)(loadCookbook = true)
            auth ← co.fold(Future.successful(false))(c ⇒ authorize(user, c.getWriteAuthorisation))
            result ← co.fold(Future.successful(NotFound("No such cookbook resource!")))(c ⇒ jsonBody.fold(Future.successful(BadRequest("Could not extract json from request body!"))) {
              json ⇒
                Parse.decodeEither[CookbookResource](json) match {
                  case -\/(failure) ⇒
                    log.error("Unable to parse json for cookbook resource! {}", failure)
                    Future.successful(BadRequest("Unable to parse json!"))
                  case \/-(editedCookbookResource) ⇒
                    val changedTargetDfasdl = (editedCookbookResource.cookbook.target, c.cookbook.target) match {
                      case (Some(DFASDL(nId, _, _)), Some(DFASDL(oId, _, _))) ⇒ nId != oId
                      case (None, None) ⇒ false
                      case _ ⇒ true
                    }
                    val editedIds: List[String] = editedCookbookResource.cookbook.sources.map(_.id)
                    val originalIds: List[String] = c.cookbook.sources.map(_.id)
                    if (editedIds != originalIds || changedTargetDfasdl) {
                      /*
                       * The DFASDL resources for the cookbook were changed. Therefore all connected
                       * transformation configurations have to be marked dirty!
                       */
                      val _ = transformationConfigurationDAO.markDirtyByCookbookResourceId(id) // FIXME This is a side effect!
                    }
                    // FIXME We should check here if the id of the submitted resource matches the request id. But ember doesn't send the id in PUT requests.
                    cookbookResourceDAO.update(editedCookbookResource.copy(id = Option(id))).map {
                      case scala.util.Failure(t) =>
                        log.error("Could not update cookbook resource!", t)
                        BadRequest("Could not update cookbook resource!")
                      case scala.util.Success(_) =>
                        Ok(editedCookbookResource.copy(id = Option(id)).asJson.nospaces).as("application/json")
                    }
                }
            }) if auth
          } yield {
            render {
              case Accepts.Html() ⇒ MethodNotAllowed("Please use application/json.") // FIXME Move the check for accept header before(!) the logic!
              case Accepts.Json() ⇒
                if (auth)
                  result
                else
                  Forbidden("You are not authorised to access this resource!")
            }
          }
      }
  }

  /**
    * Return the list of dfasdl ids that are used by the cookbook with the given id.
    *
    * @param cookbookId The cookbook id (name).
    * @return A list of dfasdl ids.
    */
  def showDfasdlIds(cookbookId: String): Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid ⇒
          for {
            co ← cookbookResourceDAO.findByCookbookId(cookbookId)(loadCookbook = true)
            _ ← co.fold(Future.successful(false))(c ⇒ authorize(user, c.getReadAuthorisation))
            ids ← co.fold(Future.successful(Set.empty[String]))(
              c ⇒ Future.successful(
                c.cookbook.target.fold(Set.empty[String])(t ⇒ Set(t.id)) ++ c.cookbook.sources.map(_.id).toSet
              )
            )
          } yield {
            render {
              case Accepts.Html() ⇒ MethodNotAllowed("Please use application/json.") // FIXME Move the check for accept header before(!) the logic!
              case Accepts.Json() ⇒ Ok(ids.asJson.nospaces).as("application/json")
            }
          }
      }
  }

  /**
    * Helper method that queries the frontend service for a mapping suggestion.
    *
    * @param m The suggester mode that shall be used.
    * @param c The cookbook that shall be used.
    * @return A future holding an option to the cookbook with the suggested mappings.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def fetchSuggestedMapping(m: MappingSuggesterModes, c: Cookbook): Future[Option[Cookbook]] = {
    import play.api.libs.concurrent.Execution.Implicits._

    val msgO: Option[FrontendServiceMessages] = m match {
      case MappingSuggesterModes.AdvancedSemantics               ⇒ Option(FrontendServiceMessages.MappingSuggesterAdvancedSemanticRequest(Option(c)))
      case MappingSuggesterModes.Simple                          ⇒ Option(FrontendServiceMessages.MappingSuggesterSimpleRequest(Option(c)))
      case MappingSuggesterModes.SimpleWithTransformers          ⇒ None
      case MappingSuggesterModes.SimpleSemantics                 ⇒ Option(FrontendServiceMessages.MappingSuggesterSemanticRequest(Option(c)))
      case MappingSuggesterModes.SimpleSemanticsWithTransformers ⇒ None
    }
    msgO.fold(Future.successful(None: Option[Cookbook]))(
      msg ⇒ frontendSelection.ask(msg).map {
        case MappingSuggesterMessages.SuggestedMapping(suggestedCookbook, _) ⇒ Option(suggestedCookbook)
        case anyMessage ⇒
          log.error("Received an unexpected response while waiting for mapping suggestions! {}", anyMessage)
          None
      }
    )
  }

  /**
    * Try to get a mapping suggestion from an available agent.
    *
    * @param id The database id of the cookbook to use.
    * @param mode The suggestion mode that shall be used. See [[MappingSuggesterModes]] for details.
    * @return Either a cookbook in json encoded form or an error.
    */
  def suggestMappings(id: Long, mode: String): Action[AnyContent] = AsyncStack(AuthorityKey → UserAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      user.id.fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid ⇒
          val suggesterMode =
            Parse.decodeEither[MappingSuggesterModes](mode) match {
              case -\/(failure) ⇒
                log.error("Could not parse mapping suggester mode! Falling back to simple mode. {}", failure)
                MappingSuggesterModes.Simple
              case \/-(success) ⇒ success
            }
          for {
            co ← cookbookResourceDAO.findById(id)(loadCookbook = true)
            auth ← co.fold(Future.successful(false))(c ⇒ authorize(user, c.getReadAuthorisation))
            result ← co.fold(Future.successful(None: Option[Cookbook]))(c ⇒ fetchSuggestedMapping(suggesterMode, c.cookbook)) if auth
          } yield {
            render {
              case Accepts.Html() ⇒ MethodNotAllowed("Please use application/json.") // FIXME Move the check for accept header before(!) the logic!
              case Accepts.Json() ⇒
                if (auth)
                  co.fold(NotFound("The requested resource could not be found!").as("application/json"))(
                    c ⇒ Ok(result.getOrElse(c.cookbook).asJson.nospaces).as("application/json")
                  )
                else
                  Forbidden("You are not authorised to access this resource!").as("application/json")
            }
          }
      }
  }

}
