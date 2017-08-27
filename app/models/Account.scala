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

package models

/**
  * The account for a user.
  *
  * @param id A unique ID for the user account.
  * @param email The email address of the user that is used as login and must be unique.
  * @param password The hashed password of the user.
  * @param groupIds A set of group ids that the user is a member of.
  * @param isAdmin A boolean flag indicating if the user is an administrator.
  * @param watchedIntro A boolean flag indicating if the user has watched the Tensei-Data introduction.
  * @param failedLoginAttempts The number of failed login attempts since the last successful login.
  * @param lockedAt An option to a timestamp when the user account was locked. If it is empty then the user is not locked.
  * @param unlockToken An option to the unlock token that can be used to unlock a locked account.
  */
@SuppressWarnings(Array("org.wartremover.warts.Null"))
final case class Account(
    id: Option[Int],
    email: String,
    password: String,
    groupIds: Set[Int],
    isAdmin: Boolean,
    watchedIntro: Boolean,
    failedLoginAttempts: Int,
    lockedAt: Option[java.sql.Timestamp],
    unlockToken: Option[String]
) {
  require(email != null, "The email must not be null!")
  require(email.length <= 128, "The length of an email must not be larger than 128 characters!")
  require(password != null, "The password must not be null!")

}

/**
  * The companion object for the account class that holds several helper functions.
  */
object Account {

  // The length of an unlock token that will contain random generated alphanumeric characters.
  val UNLOCK_TOKEN_LENGTH = 128

  /**
    * This function generates an account from the given email and password information.
    *
    * @param email The email address of the user.
    * @param password The password of the user.
    * @return A generic user account with sane default settings.
    */
  def generateGenericAccount(email: String, password: String): Account =
    Account(
      id = None,
      email = email,
      password = password,
      groupIds = Set.empty,
      isAdmin = false,
      watchedIntro = false,
      failedLoginAttempts = 0,
      lockedAt = None,
      unlockToken = None
    )

  /**
    * Generate an unlock token and return it.
    *
    * @return A string containing `UNLOCK_TOKEN_LENGTH` random generated characters.
    */
  def generateUnlockToken: String =
    scala.util.Random.alphanumeric.take(UNLOCK_TOKEN_LENGTH).mkString

}
