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

package dao

import javax.inject.Inject

import models.{ Account, Group, UserNameInfo }
import org.mindrot.jbcrypt.BCrypt
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
  * A data access object (DAO) for anything related to authentication, e.g.
  * accounts and groups.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param dbConfigProvider The slick database provider provided via dependency injection.
  */
class AuthDAO @Inject()(override protected val configuration: Configuration,
                        override protected val dbConfigProvider: DatabaseConfigProvider)
    extends Tables(configuration, dbConfigProvider) {
  import driver.api._

  /**
    * Add the given account to the given group.
    *
    * @param a The account.
    * @param g The group.
    * @return A future returning the number of affected database rows.
    */
  def addAccountToGroup(a: Account, g: Group): Future[Int] =
    a.id.fold(Future.successful(0))(
      aid => g.id.fold(Future.successful(0))(gid => dbConfig.db.run(accountsGroups += ((aid, gid))))
    )

  /**
    * Load all accounts from the database. The list is sorted by the email address.
    *
    * @return A future holding a list of accounts.
    */
  def allAccounts: Future[Seq[Account]] = dbConfig.db.run(accounts.sortBy(_.email).result)

  /**
    * Load all groups from the database. The list is sorted by the group name.
    *
    * @return A future holding a list of groups.
    */
  def allGroups: Future[Seq[Group]] = dbConfig.db.run(groups.sortBy(_.name).result)

  /**
    * A helper method to fetch the list of [[UserNameInfo]] data from the database.
    *
    * @return A future holding the list of user name informations.
    */
  def allUsernames()(implicit context: ExecutionContext): Future[Seq[UserNameInfo]] =
    dbConfig.db
      .run(accounts.sortBy(_.email).map(r => (r.id, r.email)).result)
      .map(_.map(r => UserNameInfo(id = r._1, name = r._2)))

  /**
    * Authenticate the given email and password against the database.
    *
    * @param email The email address of an account.
    * @param password The password for the account.
    * @return A future holding an option to the authenticated account.
    */
  def authenticate(email: String,
                   password: String)(implicit context: ExecutionContext): Future[Option[Account]] =
    for {
      ao <- dbConfig.db.run(
        accounts.filter(row => row.email === email && row.lockedAt.isEmpty).result.headOption
      )
      auth <- ao.fold(Future.successful(false))(
        a =>
          a.id.fold(Future.successful(false))(
            id =>
              dbConfig.db
                .run(accounts.filter(_.id === id).map(_.password).result.headOption)
                .map(
                  f => f.fold(false)(hashedPassword => BCrypt.checkpw(password, hashedPassword))
              )
        )
      )
    } yield {
      if (auth)
        ao
      else
        None
    }

  /**
    * Count the number of accounts in the database.
    *
    * @return A future holding the number of accounts.
    */
  def countAccounts: Future[Int] = dbConfig.db.run(accounts.length.result)

  /**
    * Count the number of administrator accounts in the database.
    *
    * @return A future holding the number of administrator accounts.
    */
  def countAdministrators: Future[Int] =
    dbConfig.db.run(accounts.filter(_.isAdmin === true).countDistinct.result)

  /**
    * Count the members of all groups in the database and return a list of the
    * group ids paired with the number of their members.
    *
    * @return A future holding a list of tuples with containt the group id first and the number of members second.
    */
  def countGroupMembers: Future[Seq[(Int, Int)]] =
    dbConfig.db.run(
      groups
        .join(accountsGroups)
        .on(_.id === _.groupId)
        .map(r => (r._1.id, r._2.accountId))
        .groupBy(_._1)
        .map {
          case (groupId, groupedQ) => (groupId, groupedQ.map(_._2).length)
        }
        .sortBy(_._1)
        .result
    )

  /**
    * Count the number of administrator accounts in the database that
    * are not locked.
    *
    * @return A future holding the number of unlocked administrator accounts.
    */
  def countUnlockedAdministrators: Future[Int] =
    dbConfig.db.run(
      accounts.filter(row => row.isAdmin === true && row.lockedAt.isEmpty).countDistinct.result
    )

  /**
    * Create the given account in the database.
    *
    * @param a The account to be created.
    * @return A future holding the created account.
    */
  def createAccount(a: Account): Future[Try[Account]] = {
    val hashedPassword = BCrypt.hashpw(a.password, BCrypt.gensalt())
    val dbAccount      = a.copy(password = hashedPassword)
    dbConfig.db.run(
      ((accounts returning accounts
        .map(_.id) into ((account, id) => account.copy(id = Option(id)))) += dbAccount).asTry
    )
  }

  /**
    * Create the given group in the database.
    *
    * @param g The group to be created.
    * @return A future holding the created group.
    */
  def createGroup(g: Group): Future[Try[Group]] =
    dbConfig.db.run(
      ((groups returning groups
        .map(_.id) into ((group, id) => group.copy(id = Option(id)))) += g).asTry
    )

  /**
    * Destroy the given account in the database.
    *
    * @param a The account to be destroyed.
    * @return A future holding the affected number of database rows.
    */
  def destroyAccount(a: Account): Future[Int] =
    a.id.fold(Future.successful(0))(id => dbConfig.db.run(accounts.filter(_.id === id).delete))

  /**
    * Destroy the given group in the database.
    *
    * @param g The group to be destroyed.
    * @return A future holding the affected number of database rows.
    */
  def destroyGroup(g: Group): Future[Int] =
    g.id.fold(Future.successful(0))(id => dbConfig.db.run(groups.filter(_.id === id).delete))

  /**
    * Find the account with the given ID.
    *
    * @param id The database id of the account.
    * @return A future holding an option to the account.
    */
  def findAccountById(id: Int)(implicit context: ExecutionContext): Future[Option[Account]] =
    for {
      ao <- dbConfig.db.run(accounts.filter(_.id === id).result.headOption)
      gs <- ao.fold(Future.successful(Seq.empty[Int]))(
        a => dbConfig.db.run(accountsGroups.filter(_.accountId === id).map(_.groupId).result)
      )
    } yield ao.fold(None: Option[Account])(a => Option(a.copy(groupIds = gs.toSet)))

  /**
    * Find the account with the given unlock token.
    *
    * @param t An unlock token.
    * @return A future holding an option to the account.
    */
  def findAccountByUnlockToken(t: String): Future[Option[Account]] =
    dbConfig.db.run(accounts.filter(_.unlockToken === t).result.headOption)

  /**
    * Find the group with the given ID.
    *
    * @param id The database ID of the group.
    * @return A future holding an option to the group.
    */
  def findGroupById(id: Int): Future[Option[Group]] =
    dbConfig.db.run(groups.filter(_.id === id).result.headOption)

  /**
    * Find the group with the given name.
    *
    * @param n The unique name of the group.
    * @return A future holding an option to the group.
    */
  def findGroupByName(n: String): Future[Option[Group]] =
    dbConfig.db.run(groups.filter(_.name === n).result.headOption)

  /**
    * Check if an administrator account is defined in the database.
    *
    * @return A future holding either `true` or `false`.
    */
  def hasAdminAccount: Future[Boolean] =
    dbConfig.db.run(accounts.filter(_.isAdmin === true).exists.result)

  /**
    * Increment the number of failed login attempts for the user with the given email address.
    *
    * @param email The unique email address of the user.
    * @param context An implicit execution context.
    * @return A future holding the number of affected database rows.
    */
  def incrementFailedLoginAttempts(
      email: String
  )(implicit context: ExecutionContext): Future[Int] = {
    val ao = dbConfig.db.run(accounts.filter(_.email === email).result.headOption)
    for {
      a <- ao
      inc <- a.fold(Future.successful(0))(
        d =>
          d.id.fold(Future.successful(0))(
            id =>
              dbConfig.db.run(
                accounts
                  .filter(_.id === id)
                  .map(_.failedLoginAttempts)
                  .update(d.failedLoginAttempts + 1)
            )
        )
      )
    } yield inc
  }

  /**
    * Load the ids of the groups the given account is a member of and attach
    * them to the then returned account.
    *
    * @param a An account.
    * @return A future holding the account with added group ids.
    */
  def loadGroupIds(a: Account)(implicit context: ExecutionContext): Future[Account] =
    a.id.fold(Future.successful(a))(
      id =>
        dbConfig.db
          .run(accountsGroups.filter(_.accountId === id).map(_.groupId).result)
          .map(
            gids => a.copy(groupIds = gids.toSet)
        )
    )

  /**
    * Remove the given account from the given group.
    *
    * @param a The account.
    * @param g The group.
    * @return A future holding the number of affected database rows.
    */
  def removeAccountFromGroup(a: Account, g: Group): Future[Int] =
    a.id.fold(Future.successful(0))(
      aid =>
        g.id.fold(Future.successful(0))(
          gid =>
            dbConfig.db.run(
              accountsGroups.filter(row => row.accountId === aid && row.groupId === gid).delete
          )
      )
    )

  /**
    * Unlock a given account.
    *
    * @param a The account to be unlocked.
    * @return A future holding an option to the unlocked account.
    */
  def unlockAccount(a: Account)(implicit context: ExecutionContext): Future[Option[Account]] = {
    val nop: Option[Account] = None
    a.id.fold(Future.successful(nop))(
      id =>
        dbConfig.db
          .run(
            accounts
              .filter(_.id === id)
              .map(row => (row.lockedAt, row.unlockToken))
              .update((None, None))
          )
          .flatMap(r => findAccountById(id))
    )
  }

  /**
    * Unlock a given account and set it's password.
    *
    * @param a The account that has it's password to be set and to be unlocked.
    * @return A future holding an option to the unlocked account.
    */
  def unlockAccountAndSetPassword(
      a: Account
  )(implicit context: ExecutionContext): Future[Option[Account]] = {
    val nop: Option[Account] = None
    a.id.fold(Future.successful(nop)) { id =>
      val hashedPassword = BCrypt.hashpw(a.password, BCrypt.gensalt())
      dbConfig.db
        .run(
          accounts
            .filter(_.id === id)
            .map(row => (row.password, row.lockedAt, row.unlockToken))
            .update((hashedPassword, None, None))
        )
        .flatMap(r => findAccountById(id))
    }
  }

  /**
    * Update the given account in the database.
    * '''This function currently only updates the email and the isAdmin field!'''
    *
    * @param a The account to be updated.
    * @return A future holding the number of affected rows.
    */
  def updateAccount(a: Account): Future[Int] =
    a.id.fold(Future.successful(0))(
      id =>
        dbConfig.db.run(
          accounts.filter(_.id === id).map(r => (r.email, r.isAdmin)).update((a.email, a.isAdmin))
      )
    )

  /**
    * Update the password of the given account in the database.
    *
    * @param a The account to be updated.
    * @return A future holding the number of affected database rows.
    */
  def updateAccountPassword(a: Account): Future[Int] = {
    val hashedPassword = BCrypt.hashpw(a.password, BCrypt.gensalt())
    a.id.fold(Future.successful(0))(
      id => dbConfig.db.run(accounts.filter(_.id === id).map(_.password).update(hashedPassword))
    )
  }

  /**
    * Update the given group in the database. Actually only the name will be updated.
    *
    * @param g The group to be updated.
    * @return A future holding the number of affected database rows.
    */
  def updateGroup(g: Group): Future[Int] =
    g.id.fold(Future.successful(0))(
      id => dbConfig.db.run(groups.filter(_.id === id).map(_.name).update(g.name))
    )

  /**
    * Update the given account and set the `watchedIntro` flag to `true`.
    *
    * @param a The account to be updated.
    * @return A future holding the number of affected database rows.
    */
  def watchedIntro(a: Account): Future[Int] =
    a.id.fold(Future.successful(0))(
      id => dbConfig.db.run(accounts.filter(_.id === id).map(r => r.watchedIntro).update(true))
    )

}
