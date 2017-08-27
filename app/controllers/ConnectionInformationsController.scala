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
import com.wegtam.tensei.adt.ConnectionInformation
import dao.{ AuthDAO, ConnectionInformationResourceDAO, CookbookResourceDAO }
import jp.t2v.lab.play2.auth.AuthElement
import models.{ ConnectionInformationResource, Permission }
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, Controller }
import argonaut.Argonaut._
import forms.{ ConnectionInformationResourceForm, ResourceAuthorisationFieldsForm }
import models.Authorities.UserAuthority
import play.filters.csrf.CSRF

import scala.concurrent.{ ExecutionContext, Future }

/**
  * The controller provides functionalities to manage connection informations.
  *
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param cookbookResourceDAO The DAO for handling cookbook resources provided via dependency injection.
  * @param connectionInformationResourceDAO The DAO for handling connection informations provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class ConnectionInformationsController @Inject()(
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    cookbookResourceDAO: CookbookResourceDAO,
    connectionInformationResourceDAO: ConnectionInformationResourceDAO,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport {

  private val log = Logger.logger

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
    * Display the form for creating a new connection information resource.
    *
    * @return The form or an error page.
    */
  def add: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
      implicit token =>
        val user      = loggedIn
        val getUsers  = authDAO.allUsernames
        val getGroups = authDAO.allGroups
        for {
          as <- getUsers
          gs <- getGroups
        } yield {
          user.id.fold(InternalServerError(views.html.dashboard.errors.serverError())) { uid =>
            val authorisation = ResourceAuthorisationFieldsForm.Data(
              ownerId = uid,
              groupId = user.groupIds.headOption,
              groupPermissions = Permission.DEFAULT_GROUP_PERMISSIONS,
              worldPermissions = Permission.DEFAULT_WORLD_PERMISSIONS
            )
            val formData = ConnectionInformationResourceForm.Data(
              uri = new java.net.URI(""),
              language_tag = None,
              username = None,
              password = None,
              checksum = None,
              authorisation = authorisation
            )
            Ok(
              views.html.dashboard.connectioninformations.add(
                ConnectionInformationResourceForm.form.fill(formData),
                ConnectionInformationResourceForm.AvailableLocales,
                gs,
                as
              )
            )
          }
        }
    }
  }

  // A helper form for our autocomplete function.
  val autocompleteUriForm = Form(play.api.data.Forms.single("term" -> text))

  /**
    * A server side helper function for autocompletion of the connection uri.
    * This function expects a json string in the body of the POST request
    * that contains the uri which may be empty.
    *
    * @return A json string containing completion suggestions.
    */
  def autoComplete: Action[AnyContent] = StackAction(AuthorityKey -> UserAuthority) {
    implicit request =>
      val uriSuggestions = List(
        "file://",
        "jdbc:",
        "jdbc:derby:",
        "jdbc:h2:",
        "jdbc:hsqldb:",
        "jdbc:mariadb:",
        "jdbc:mysql:",
        "jdbc:oracle:thin:",
        "jdbc:postgresql:",
        "jdbc:sapdb:",
        "jdbc:sqlite:",
        "jdbc:sqlserver:",
        "http://",
        "https://",
        "sftp://",
        "ftp://",
        "smb://"
      )
      val suggestions: List[String] =
        autocompleteUriForm.bindFromRequest.fold(
          formWithErrors =>
            List("file://", "jdbc:", "http://", "https://", "sftp://", "ftp://", "smb://"),
          uri =>
            if (uri.isEmpty)
              List("file://", "jdbc:", "http://", "https://", "sftp://", "ftp://", "smb://")
            else
              uriSuggestions.filter(_.startsWith(uri))
        )

      val json = Json.toJson(suggestions)
      Ok(json)
  }

  /**
    * Try to create a connection information resource in the database from
    * the submitted form data.
    *
    * @todo Only use the owner id from the form if the current user is an administrator.
    * @return Redirect to the detail page or an error page.
    */
  def create: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
      implicit token =>
        val user = loggedIn
        user.id.fold(
          Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
        ) { uid =>
          val getUsers  = authDAO.allUsernames
          val getGroups = authDAO.allGroups
          ConnectionInformationResourceForm.form
            .bindFromRequest()
            .fold(
              formWithErrors => {
                for {
                  as <- getUsers
                  gs <- getGroups
                } yield
                  BadRequest(
                    views.html.dashboard.connectioninformations
                      .add(formWithErrors,
                           ConnectionInformationResourceForm.AvailableLocales,
                           gs,
                           as)
                  )
              },
              formData => {
                val connection = ConnectionInformation(
                  uri = formData.uri,
                  dfasdlRef = None,
                  username = formData.username,
                  password = formData.password,
                  checksum = formData.checksum,
                  languageTag = formData.language_tag
                )
                val owner = if (user.isAdmin) formData.authorisation.ownerId else uid
                val r = ConnectionInformationResource(
                  id = None,
                  connection = connection,
                  ownerId = owner,
                  groupId = formData.authorisation.groupId,
                  groupPermissions = formData.authorisation.groupPermissions,
                  worldPermissions = formData.authorisation.worldPermissions
                )
                connectionInformationResourceDAO.create(r).map {
                  case scala.util.Failure(e) =>
                    log.error("Could not create connection information!", e)
                    Redirect(routes.ConnectionInformationsController.index())
                      .flashing("error" -> "Could not create resource!")
                  case scala.util.Success(c) =>
                    c.id.fold(
                      Redirect(routes.ConnectionInformationsController.index())
                        .flashing("error" -> "Could not create resource!")
                    )(
                      id =>
                        Redirect(routes.ConnectionInformationsController.show(id))
                          .flashing("success" -> "Resource created.")
                    )
                }
              }
            )
        }
    }
  }

  /**
    * Destroy the connection information resource with the given ID.
    *
    * @param id The ID of a connection information resource.
    * @return Redirect to the connection informations list flashing success or failure or an error page.
    */
  def destroy(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user = loggedIn
      for {
        co   <- connectionInformationResourceDAO.findById(id)
        auth <- co.fold(Future.successful(false))(c => authorize(user, c.getWriteAuthorisation))
        r    <- co.fold(Future.successful(0))(c => connectionInformationResourceDAO.destroy(c)) if auth
      } yield {
        if (auth) {
          if (r > 0)
            Redirect(routes.ConnectionInformationsController.index()).flashing(
              "success" -> Messages("ui.model.deleted", Messages("models.connectionInformation"))
            )
          else
            Redirect(routes.ConnectionInformationsController.index())
              .flashing("error" -> "No entry was deleted from the database!")
        } else
          Forbidden(views.html.errors.forbidden())
      }
  }

  /**
    * Display the form to edit a connection information resource.
    *
    * @param id The ID of the connection information resource.
    * @return The form to edit the resource or an error page.
    */
  def edit(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token =>
          val user      = loggedIn
          val getUsers  = authDAO.allUsernames
          val getEntry  = connectionInformationResourceDAO.findById(id)
          val getGroups = authDAO.allGroups
          for {
            as   <- getUsers
            co   <- getEntry
            gs   <- getGroups
            auth <- co.fold(Future.successful(false))(c => authorize(user, c.getWriteAuthorisation))
          } yield {
            if (auth)
              co.fold(
                NotFound(
                  views.html.errors.notFound(Messages("errors.notfound.title"),
                                             Option(Messages("errors.notfound.header")))
                )
              ) { c =>
                val authorisation = ResourceAuthorisationFieldsForm.Data(
                  ownerId = c.ownerId,
                  groupId = c.groupId,
                  groupPermissions = c.groupPermissions,
                  worldPermissions = c.worldPermissions
                )
                val formData = ConnectionInformationResourceForm.Data(
                  uri = c.connection.uri,
                  language_tag = c.connection.languageTag,
                  username = c.connection.username,
                  password = c.connection.password,
                  checksum = c.connection.checksum,
                  authorisation = authorisation
                )
                Ok(
                  views.html.dashboard.connectioninformations.edit(
                    id,
                    ConnectionInformationResourceForm.form.fill(formData),
                    ConnectionInformationResourceForm.AvailableLocales,
                    gs,
                    as
                  )
                )
              } else
              Forbidden(views.html.errors.forbidden())
          }
      }
  }

  /**
    * List all connection information resources that the user is able to read.
    *
    * @return A page listing all readable resources.
    */
  def index: Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    loggedIn.id.fold(
      Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))
    ) { uid =>
      connectionInformationResourceDAO.allReadable(uid, loggedIn.groupIds).map { cs =>
        Ok(views.html.dashboard.connectioninformations.index(cs))
      }
    }
  }

  /**
    * Show a detail page of the connection information resource with the given id.
    *
    * @param id The ID of the connection information resource.
    * @return The detail page or an error page.
    */
  def show(id: Long): Action[AnyContent] = AsyncStack(AuthorityKey -> UserAuthority) {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits._

      val user      = loggedIn
      val getUsers  = authDAO.allUsernames()
      val getGroups = authDAO.allGroups
      for {
        co   <- connectionInformationResourceDAO.findById(id)
        auth <- co.fold(Future.successful(false))(c => authorize(user, c.getReadAuthorisation))
        as   <- getUsers if auth
        gs   <- getGroups if auth
      } yield {
        if (auth) {
          co.fold(
            NotFound(
              views.html.errors.notFound(Messages("errors.notfound.title"),
                                         Option(Messages("errors.notfound.header")))
            )
          )(
            c => Ok(views.html.dashboard.connectioninformations.show(c, gs.toSet, as.toSet))
          )
        } else
          Forbidden(views.html.errors.forbidden())
      }
  }

  /**
    * Try to update the connection information resource with the given id using the
    * submitted form data.
    *
    * @todo Only use the owner id from the form if the current user is an administrator.
    * @param id The ID of the connection information resource.
    * @return Redirect to the detail page flashing success or error or display an error page.
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
            val getUsers  = authDAO.allUsernames
            val getEntry  = connectionInformationResourceDAO.findById(id)
            val getGroups = authDAO.allGroups
            for {
              as <- getUsers
              gs <- getGroups
              co <- getEntry
              auth <- co.fold(Future.successful(false))(
                c => authorize(user, c.getWriteAuthorisation)
              )
              cu <- co.fold(
                Future.successful(
                  NotFound(
                    views.html.errors.notFound(Messages("errors.notfound.title"),
                                               Option(Messages("errors.notfound.header")))
                  )
                )
              )(
                c =>
                  ConnectionInformationResourceForm.form
                    .bindFromRequest()
                    .fold(
                      formWithErrors =>
                        Future.successful(
                          BadRequest(
                            views.html.dashboard.connectioninformations
                              .edit(id,
                                    formWithErrors,
                                    ConnectionInformationResourceForm.AvailableLocales,
                                    gs,
                                    as)
                          )
                      ),
                      formData => {
                        val connection = ConnectionInformation(
                          uri = formData.uri,
                          dfasdlRef = None,
                          username = formData.username,
                          password = formData.password,
                          checksum = formData.checksum,
                          languageTag = formData.language_tag
                        )
                        val owner =
                          if (user.isAdmin) formData.authorisation.ownerId else uid
                        val r = ConnectionInformationResource(
                          id = c.id,
                          connection = connection,
                          ownerId = owner,
                          groupId = formData.authorisation.groupId,
                          groupPermissions = formData.authorisation.groupPermissions,
                          worldPermissions = formData.authorisation.worldPermissions
                        )
                        connectionInformationResourceDAO
                          .update(r)
                          .map(
                            f =>
                              c.id
                                .fold(
                                  InternalServerError(views.html.dashboard.errors.serverError())
                                )(
                                  cid =>
                                    Redirect(routes.ConnectionInformationsController.show(cid))
                                      .flashing("success" -> "Resource updated.")
                              )
                          )
                      }
                  )
              ) if auth
            } yield {
              if (auth)
                cu
              else
                Forbidden(views.html.errors.forbidden())
            }
          }
      }
  }
}
