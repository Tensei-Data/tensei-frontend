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
import dao.{ AuthDAO, WorkHistoryDAO }
import jp.t2v.lab.play2.auth.AuthElement
import models.Authorities.UserAuthority
import models.WorkHistoryStatistics
import play.api.Configuration
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.Controller

import scala.concurrent.{ ExecutionContext, Future }

/**
  * This controller provides functionalities for operating the work history.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param messagesApi Provides messages for translation and internationalisation provided via dependency injection.
  * @param authDAO The DAO for operating with accounts and groups provided via dependency injection.
  * @param workHistoryDAO The DAO for handling the work history provided via dependency injection.
  * @param webJarAssets A play framework controller that is able to resolve webjar assets provided via dependency injection.
  */
class WorkHistoryController @Inject()(
    protected val configuration: Configuration,
    val messagesApi: MessagesApi,
    authDAO: AuthDAO,
    workHistoryDAO: WorkHistoryDAO,
    implicit val webJarAssets: WebJarAssets
) extends Controller
    with AuthElement
    with AuthConfigImpl
    with I18nSupport {
  // The number of history entries that should be loaded per page.
  val entriesPerPage = configuration.getInt("tensei.frontend.ui.queue-hist-per-page").getOrElse(30)

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
    * Show the work history as readable by the user.
    *
    * @param page Used for paginating the history.
    * @return The history entries readable by the current user according to the given page.
    */
  def index(page: Int) = AsyncStack(AuthorityKey -> UserAuthority) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._

    val user = loggedIn
    user.id
      .fold(Future.successful(InternalServerError(views.html.dashboard.errors.serverError()))) {
        uid =>
          val getCount = workHistoryDAO.countReadable(uid, user.groupIds)
          val skip     = page * entriesPerPage
          for {
            cnt    <- getCount
            hs     <- workHistoryDAO.allReadableWithLimits(uid, user.groupIds)(skip, entriesPerPage)
            stats  <- workHistoryDAO.calculateStatistics(uid, user.groupIds)
            tStats <- WorkHistoryStatistics.translateStats(stats)
          } yield {
            val maxPages = {
              val rest   = cnt % entriesPerPage
              val rawCnt = cnt / entriesPerPage
              if (rest > 0 && rawCnt > 1)
                rawCnt + 1
              else
                rawCnt
            }
            Ok(views.html.dashboard.workHistory.index(hs, maxPages, page, tStats))
          }
      }
  }

}
