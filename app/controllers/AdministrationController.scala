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
import java.time.{LocalDateTime, Period}
import java.util.zip.GZIPInputStream

import actors.FrontendService
import actors.FrontendService.FrontendServiceMessages
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.Inject
import com.wegtam.tensei.adt.{LicenseValidationResult, TenseiLicenseMessages}
import controllers.AdministrationController.{LicenseEntitiesData, LicenseMetaData}
import dao.AuthDAO
import forms.AccountForms
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.{AdminAuthority, UserWithIdAuthority}
import models.{Account, Group}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, Controller, Result}
import play.api.{Configuration, Logger}
import play.filters.csrf.CSRF

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scalaz._

/**
  * The controller provides functionalities for the administration of the tensei frontend.
  * This includes accounts, groups and license management.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param system The actor system used by the play application provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class AdministrationController @Inject() (
    protected val configuration: Configuration,
    val messagesApi:             MessagesApi,
    authDAO:                     AuthDAO,
    implicit val system:         ActorSystem,
    implicit val webJarAssets:   WebJarAssets
) extends Controller with AuthElement with AuthConfigImpl with I18nSupport {
  private final val LICENSE_ENCODING: Charset = StandardCharsets.UTF_8

  private val log = Logger.logger

  private val DEFAULT_DB_TIMEOUT = 10000L // The fallback default timeout for database operations in milliseconds.
  private val DEFAULT_ASK_TIMEOUT = 5000L // The fallback default timeout for `ask` operations in milliseconds.

  private val frontendSelection = system.actorSelection(s"/user/${FrontendService.name}")
  implicit val timeout: Timeout = Timeout(FiniteDuration(configuration.getMilliseconds("tensei.frontend.ask-timeout").getOrElse(DEFAULT_ASK_TIMEOUT), MILLISECONDS))
  lazy val dbTimeout = FiniteDuration(configuration.getMilliseconds("tensei.frontend.db-timeout").getOrElse(DEFAULT_DB_TIMEOUT), MILLISECONDS)

  /**
    * A function that returns a `User` object from an `Id`.
    * @todo Currently we override this method from the trait `AuthConfigImpl` because we can't use dependecy injection there to get the appropriate DAO.
    *
    * @param id  The unique database id e.g. primary key for the user.
    * @param context The execution context.
    * @return An option to the user wrapped into a future.
    */
  override def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]] = authDAO.findAccountById(id)

  /**
    * Display the html form to add an account.
    *
    * @return The website containing the html form for adding an account.
    */
  def addAccount(): Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          val definedUsers = authDAO.countAccounts
          val numberOfAllowedUsers: Future[TenseiLicenseMessages] = (frontendSelection ? TenseiLicenseMessages.ReportAllowedNumberOfUsers).mapTo[TenseiLicenseMessages]
          for {
            count ← definedUsers
            na ← numberOfAllowedUsers
          } yield {
            val allowed: Option[Int] = na match {
              case TenseiLicenseMessages.AllowedNumberOfUsers(cnt) ⇒ Option(cnt)
              case _ ⇒ None
            }
            val canSave = count < allowed.getOrElse(0)
            Ok(views.html.dashboard.accounts.create(AccountForms.adminForm, canSave, allowed))
          }
      }
  }

  /**
    * Display the html form to add a group.
    *
    * @return The form for adding a group.
    */
  def addGroup(): Action[AnyContent] = StackAction(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      CSRF.getToken.fold(Forbidden(views.html.errors.forbidden())) {
        implicit token ⇒
          Ok(views.html.dashboard.groups.add(AccountForms.groupForm))
      }
  }

  /**
    * List all accounts.
    *
    * @return A page listing all accounts from the database.
    */
  def accounts: Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val accountsInDatabase = authDAO.allAccounts
      val numberOfAllowedUsers: Future[TenseiLicenseMessages] = (frontendSelection ? TenseiLicenseMessages.ReportAllowedNumberOfUsers).mapTo[TenseiLicenseMessages]
      for {
        as ← accountsInDatabase
        na ← numberOfAllowedUsers
        gs ← authDAO.allGroups
      } yield {
        val allowed: Option[Int] = na match {
          case TenseiLicenseMessages.AllowedNumberOfUsers(count) ⇒ Option(count)
          case _ ⇒ None
        }
        val groupNames = gs.flatMap(g ⇒ g.id.fold(None: Option[(Int, String)])(id ⇒ Option(id → g.name))).toMap
        Ok(views.html.dashboard.accounts.index(as, allowed, groupNames))
      }
  }

  /**
    * Try to create an account from the submitted form data.
    * A newly created account always gets a random generated password and is locked.
    *
    * @return Redirect to the account list or give a bad request if an error occured.
    */
  def createAccount: Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          val definedUsers = authDAO.countAccounts
          val numberOfAllowedUsers: Future[TenseiLicenseMessages] = (frontendSelection ? TenseiLicenseMessages.ReportAllowedNumberOfUsers).mapTo[TenseiLicenseMessages]
          for {
            count ← definedUsers
            na ← numberOfAllowedUsers
            result ← na match {
              case TenseiLicenseMessages.AllowedNumberOfUsers(allowed) ⇒
                if (count < allowed) {
                  AccountForms.adminForm.bindFromRequest().fold(
                    formWithErrors ⇒ Future.successful(BadRequest(views.html.dashboard.accounts.create(formWithErrors, canSave = true, Option(allowed)))),
                    formData ⇒ {
                      val pw = scala.util.Random.alphanumeric.take(16).toString()
                      val ut = Account.generateUnlockToken
                      val la = java.sql.Timestamp.valueOf(LocalDateTime.now())
                      val account = Account(
                        id = None,
                        email = formData.email._1,
                        password = pw,
                        groupIds = Set.empty,
                        isAdmin = formData.isAdmin,
                        watchedIntro = false,
                        failedLoginAttempts = 0,
                        lockedAt = Option(la),
                        unlockToken = Option(ut)
                      )
                      authDAO.createAccount(account).map {
                        case scala.util.Failure(e) ⇒
                          log.error("Could not create account!", e)
                          Redirect(routes.AdministrationController.accounts()).flashing("error" → "Could not create account!")
                        case scala.util.Success(_) ⇒
                          Redirect(routes.AdministrationController.accounts()).flashing("success" → "Created account...")
                      }
                    }
                  )
                }
                else
                  Future.successful(Redirect(routes.AdministrationController.accounts()).flashing("error" → "Maximum number of accounts reached!"))
              case _ ⇒ Future.successful(Redirect(routes.AdministrationController.accounts()).flashing("error" → "Maximum number of accounts reached!"))
            }
          } yield result
      }
  }

  /**
    * Try to create a group from the submitted form data.
    *
    * @return Redirect to the groups page flashing success or failure or display an error page.
    */
  def createGroup: Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          AccountForms.groupForm.bindFromRequest().fold(
            formWithErrors ⇒ Future.successful(BadRequest(views.html.dashboard.groups.add(formWithErrors))),
            formData ⇒ for {
              go ← authDAO.findGroupByName(formData.name)
              result ← go.fold {
                val g = Group(id = None, name = formData.name)
                authDAO.createGroup(g).map {
                  case scala.util.Failure(e) ⇒
                    log.error("Could not create group!", e)
                    Redirect(routes.AdministrationController.groups()).flashing("error" → "Could not create group!")
                  case scala.util.Success(r) ⇒ r.id.fold(
                    Redirect(routes.AdministrationController.groups()).flashing("error" → "Could not create group!")
                  )(_ ⇒ Redirect(routes.AdministrationController.groups()).flashing("success" → "Created group."))
                }
              } {
                g ⇒ Future.successful(BadRequest(views.html.dashboard.groups.add(AccountForms.groupForm.fill(formData).withError("name", s"Group name already exists! `${g.name}`"))))
              }
            } yield result
          )
      }
  }

  /**
    * Destroy the account with the given ID.
    * It is not possible to delete the own account or the last admin account.
    *
    * @param id The database id of the account.
    * @return Either redirect to the accounts page flashing success of failure or a not found page.
    */
  def destroyAccount(id: Int): Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      authDAO.findAccountById(id).flatMap(
        ao ⇒
          ao.fold(Future.successful(NotFound(views.html.errors.notFound(Messages("errors.notfound.title")))))(
            a ⇒
              if (a.id == loggedIn.id)
                Future.successful(Redirect(routes.AdministrationController.accounts()).flashing("error" → "Thou shall not delete thy own account!"))
              else
                for {
                  activeAdmins ← authDAO.countUnlockedAdministrators
                  r ← if (activeAdmins > 1) authDAO.destroyAccount(a).map(_ ⇒ Redirect(routes.AdministrationController.accounts()).flashing("success" → Messages("ui.model.deleted", Messages("models.account")))) else Future.successful(Redirect(routes.AdministrationController.accounts()).flashing("error" → Messages("ui.account.oneAdminDelete")))
                } yield r
          )
      )
  }

  /**
    * Destroy the group with the given ID.
    *
    * @param id The database id of the group.
    * @return Redirect to the groups page flashing success or failure or display a not found error.
    */
  def destroyGroup(id: Int): Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      for {
        go ← authDAO.findGroupById(id)
        result ← go.fold(Future.successful(NotFound(views.html.errors.notFound(Messages("errors.notfound.title")))))(
          g ⇒ authDAO.destroyGroup(g).map(
            r ⇒
              if (r > 0)
                Redirect(routes.AdministrationController.groups()).flashing("success" → "Group deleted.")
              else
                Redirect(routes.AdministrationController.groups()).flashing("error" → "Could not delete group!")
          )
        )
      } yield result
  }

  /**
    * Display the form to update an account.
    * A regular user can only edit his/her own account and cannot change much.
    * An administrator can edit any account and can change the admin flag on the account.
    * However an admin is not permitted to edit the admin flag of his/her own account.
    *
    * @param id The database ID of the account.
    * @return The website containing the edit form or an error page.
    */
  def editAccount(id: Int): Action[AnyContent] = AsyncStack(AuthorityKey → UserWithIdAuthority(id)) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          authDAO.findAccountById(id).map(
            ao ⇒
              ao.fold(NotFound(views.html.errors.notFound(Messages("errors.notfound.title"), Option(Messages("errors.notfound.account")))))(
                a ⇒
                  a.id.fold(NotFound(views.html.errors.notFound(Messages("errors.notfound.title"), Option(Messages("errors.notfound.account")))))(
                    id ⇒
                      if (loggedIn.isAdmin && loggedIn.id != a.id)
                        Ok(views.html.dashboard.accounts.editAdmin(id, AccountForms.adminForm.fill(AccountForms.AdminData((a.email, a.email), a.isAdmin))))
                      else
                        Ok(views.html.dashboard.accounts.edit(id, AccountForms.profileForm.fill(AccountForms.ProfileData((a.email, a.email)))))
                  )
              )
          )
      }
  }

  /**
    * Display the form to edit a group with the given ID.
    *
    * @param id The database id of the group.
    * @return The form to edit the group or an error page.
    */
  def editGroup(id: Int): Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          authDAO.findGroupById(id).map(
            go ⇒ go.fold(NotFound(views.html.errors.notFound(Messages("errors.notfound.title")))) {
              g ⇒
                val d = AccountForms.GroupData(name = g.name)
                Ok(views.html.dashboard.groups.edit(id, AccountForms.groupForm.fill(d)))
            }
          )
      }
  }

  /**
    * List all defined groups.
    *
    * @return A list of all available groups.
    */
  def groups: Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      for {
        gs ← authDAO.allGroups
        cs ← authDAO.countGroupMembers
      } yield Ok(views.html.dashboard.groups.index(gs, cs.toMap))
  }

  /**
    * Display information about the installed license.
    *
    * @return A page with the license information.
    */
  def license: Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      val metaData: Future[TenseiLicenseMessages] = (frontendSelection ? TenseiLicenseMessages.ReportLicenseMetaData).mapTo[TenseiLicenseMessages]
      val entityData: Future[TenseiLicenseMessages] = (frontendSelection ? TenseiLicenseMessages.ReportLicenseEntitiesData).mapTo[TenseiLicenseMessages]

      for {
        md ← metaData
        ed ← entityData
      } yield {
        val meta: Option[LicenseMetaData] = md match {
          case TenseiLicenseMessages.LicenseMetaData(id, licensee, period) ⇒ Option(LicenseMetaData(id, licensee, period))
          case _ ⇒
            log.error("Received an unexpected message while waiting for license metadata! {}", md)
            None
        }
        val entity: Option[LicenseEntitiesData] = ed match {
          case TenseiLicenseMessages.LicenseEntitiesData(agents, configurations, users, cronjobs, trigger) ⇒ Option(LicenseEntitiesData(agents, configurations, users, cronjobs, trigger))
          case _ ⇒
            log.error("Received an unexpected message while waiting for license entities data! {}", ed)
            None
        }
        Ok(views.html.dashboard.license.overview(meta, entity))
      }
  }

  /**
    * Display the form for uploading a license file.
    *
    * @return The form for a license upload.
    */
  def licenseUpdate: Action[AnyContent] = StackAction(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      CSRF.getToken.fold(Forbidden(views.html.errors.forbidden()))(
        implicit token ⇒
          Ok(views.html.dashboard.license.update())
      )
  }

  /**
    * Validate and update the uploaded license file.
    *
    * @return Redirect either to the license page or to the upload form flashing success or error messages.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def licenseImport: Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden())))(
        implicit token ⇒
          request.body.asMultipartFormData.fold(Future.successful(Redirect(routes.AdministrationController.licenseUpdate()).flashing("error" → "Could not parse form data!")))(
            formData ⇒
              formData.file("licenseFile").fold(Future.successful(Redirect(routes.AdministrationController.licenseUpdate()).flashing("error" → "No license file found in form data!"))) {
                tempFile ⇒
                  val encodedLicense = scala.io.Source.fromInputStream(new GZIPInputStream(Files.newInputStream(tempFile.ref.file.toPath)), LICENSE_ENCODING.name()).mkString
                  val validateLicense = frontendSelection ? TenseiLicenseMessages.ValidateLicense(encodedLicense)
                  for {
                    vresult ← validateLicense
                    result: Result ← vresult match {
                      case TenseiLicenseMessages.ValidateLicenseResult(r) ⇒
                        r match {
                          case LicenseValidationResult.Invalid(reason) ⇒
                            log.error("Invalid license! {}", reason)
                            Future.successful(Redirect(routes.AdministrationController.licenseUpdate()).flashing("error" → "Invalid license!"))
                          case LicenseValidationResult.Valid ⇒
                            (frontendSelection ? TenseiLicenseMessages.UpdateLicense(encodedLicense)).map {
                              case TenseiLicenseMessages.UpdateLicenseResult(res) ⇒
                                res match {
                                  case -\/(failure) ⇒
                                    log.error("Failed to update license! {}", failure)
                                    Redirect(routes.AdministrationController.licenseUpdate()).flashing("error" → failure)
                                  case \/-(success) ⇒
                                    log.info("Updated license. {}", success)
                                    Redirect(routes.AdministrationController.license()).flashing("success" → success)
                                }
                              case msg ⇒
                                log.error("Received unexpected response while updating license! {}", msg)
                                Redirect(routes.AdministrationController.licenseUpdate()).flashing("error" → "Failed to update license due to an unexpected error!")
                            }
                        }
                      case FrontendServiceMessages.ServerConnectionError(e) ⇒
                        log.error(e)
                        Future.successful(Redirect(routes.AdministrationController.licenseUpdate()).flashing("error" → e))
                      case _ ⇒
                        log.error("Received unexpected response while validating license! {}", vresult)
                        Future.successful(Redirect(routes.AdministrationController.licenseUpdate()).flashing("error" → "Failed to validate license due to an unexpected error!"))
                    }
                  } yield result
              }
          )
      )
  }

  /**
    * Try to update an account from submitted form data.
    *
    * @param id The database ID of the account.
    * @return Redirect to the accounts page flashing the status or a bad request if an error occured.
    */
  def updateAccount(id: Int): Action[AnyContent] = AsyncStack(AuthorityKey → UserWithIdAuthority(id)) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          val account = authDAO.findAccountById(id)
          for {
            ac ← account
            result ← ac.fold(Future.successful(NotFound(views.html.errors.notFound(Messages("errors.notfound.title"), Option(Messages("errors.notfound.account"))))))(
              a ⇒
                a.id.fold(Future.successful(NotFound(views.html.errors.notFound(Messages("errors.notfound.title"), Option(Messages("errors.notfound.account")))))) {
                  _ ⇒
                    if (loggedIn.isAdmin && loggedIn.id != a.id)
                      AccountForms.adminForm.bindFromRequest().fold(
                        formWithErrors ⇒ Future.successful(BadRequest(views.html.dashboard.accounts.editAdmin(id, formWithErrors))),
                        formData ⇒ {
                          val updatedA = a.copy(email = formData.email._1, isAdmin = formData.isAdmin)
                          authDAO.updateAccount(updatedA).map(_ ⇒ Redirect(routes.AdministrationController.accounts()).flashing("success" → "Updated account."))
                        }
                      )
                    else
                      AccountForms.profileForm.bindFromRequest().fold(
                        formWithErrors ⇒ Future.successful(BadRequest(views.html.dashboard.accounts.edit(id, formWithErrors))),
                        formData ⇒ {
                          val updatedA = a.copy(email = formData.email._1)
                          authDAO.updateAccount(updatedA).map(_ ⇒ Redirect(routes.AdministrationController.accounts()).flashing("success" → "Updated account."))
                        }
                      )
                }
            )
          } yield result
      }
  }

  /**
    * Try to update the group with the given ID using the submitted form data.
    *
    * @param id The database id of the group.
    * @return Redirect to the groups page flashing success or failure or display an error.
    */
  def updateGroup(id: Int): Action[AnyContent] = AsyncStack(AuthorityKey → AdminAuthority) {
    implicit request ⇒
      import play.api.libs.concurrent.Execution.Implicits._

      CSRF.getToken.fold(Future.successful(Forbidden(views.html.errors.forbidden()))) {
        implicit token ⇒
          for {
            go ← authDAO.findGroupById(id)
            check ← go.fold(Future.successful(Failure(NotFound(views.html.errors.notFound(Messages("errors.notfound.title")))): Validation[Result, Group]))(
              g ⇒ AccountForms.groupForm.bindFromRequest().fold(
                formWithErrors ⇒ Future.successful(Failure(BadRequest(views.html.dashboard.groups.edit(id, formWithErrors)))),
                formData ⇒ {
                  authDAO.findGroupByName(formData.name).map(
                    o ⇒ o.fold(Success(g.copy(name = formData.name)): Validation[Result, Group])(
                      dup ⇒
                        if (dup.id == g.id)
                          Success(g.copy(name = formData.name))
                        else
                          Failure(BadRequest(views.html.dashboard.groups.add(AccountForms.groupForm.fill(formData).withError("name", "Group name exists!"))))
                    )
                  )
                }
              )
            )
            result ← check match {
              case Failure(f) ⇒ Future.successful(f)
              case Success(s) ⇒ authDAO.updateGroup(s).map(
                r ⇒
                  if (r > 0)
                    Redirect(routes.AdministrationController.groups()).flashing("success" → "Updated group.")
                  else
                    Redirect(routes.AdministrationController.groups()).flashing("error" → "Could not update group.")
              )
            }
          } yield result
      }
  }

}

object AdministrationController {

  /**
    * The number of entities that are allowed by the license.
    *
    * @param numberOfAgents The allowed number of agents.
    * @param numberOfConfigurations The allowed number of transformation configurations.
    * @param numberOfUsers The allowed number of user accounts.
    * @param numberOfCronjobs The allowed number of cronjobs.
    * @param numberOfTriggers The allowed number of triggers.
    */
  final case class LicenseEntitiesData(
    numberOfAgents:         Int,
    numberOfConfigurations: Int,
    numberOfUsers:          Int,
    numberOfCronjobs:       Int,
    numberOfTriggers:       Int
  )

  /**
    * Metadata for a tensei license.
    *
    * @param id The unique ID of the license.
    * @param licensee The name of the licensee that has registered the license.
    * @param period The period for which the license is valid.
    */
  final case class LicenseMetaData(
    id:       String,
    licensee: String,
    period:   Period
  )

}