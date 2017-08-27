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
import dao.AuthDAO
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.UserAuthority
import play.api.Configuration
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.Controller

import scala.concurrent.{ ExecutionContext, Future }

class HelpController @Inject()(
    protected val configuration: Configuration,
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport {

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

  def intro = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    Future.successful(Ok(views.html.dashboard.help.intro()))
  }

}
