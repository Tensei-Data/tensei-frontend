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

import argonaut._, Argonaut._

import scala.collection.immutable.Seq

/**
  * A sealed trait for our permission model.
  */
sealed trait Permission

object Permission {
  // Default fallback value for group permissions.
  val DEFAULT_GROUP_PERMISSIONS: Set[Permission] = Set(Permission.Read, Permission.Write)
  // Default fallback value for world permissions.
  val DEFAULT_WORLD_PERMISSIONS: Set[Permission] = Set(Permission.Read)

  /*
   * A list of permission sets that imply execute access for a resource.
   * This is needed currently to ease permission checks in Slick database queries
   * using the `inSetBind` function.
   */
  val ExecutablePermissionSets: Seq[Set[Permission]] = Vector(
    Set(Permission.Execute),
    Set(Permission.Execute, Permission.Read),
    Set(Permission.Execute, Permission.Write),
    Set(Permission.Execute, Permission.Read, Permission.Write)
  )
  /*
   * A list of permission sets that imply read access for a resource.
   * This is needed currently to ease permission checks in Slick database queries
   * using the `inSetBind` function.
   */
  val ReadablePermissionSets: Seq[Set[Permission]] = Vector(
    Set(Permission.Read),
    Set(Permission.Read, Permission.Execute),
    Set(Permission.Read, Permission.Write),
    Set(Permission.Execute, Permission.Read, Permission.Write)
  )
  /*
   * A list of permission sets that imply write access for a resource.
   * This is needed currently to ease permission checks in Slick database queries
   * using the `inSetBind` function.
   */
  val WritablePermissionSets: Seq[Set[Permission]] = Vector(
    Set(Permission.Write),
    Set(Permission.Write, Permission.Execute),
    Set(Permission.Write, Permission.Read),
    Set(Permission.Execute, Permission.Read, Permission.Write)
  )

  /**
    * The permission to execute a resource.
    */
  case object Execute extends Permission

  /**
    * The permission to read a resource.
    */
  case object Read extends Permission

  /**
    * The permission to write a resource.
    */
  case object Write extends Permission

  /**
    * Encode a given permission into an integer value.
    *
    * @param p A permission.
    * @return The integer representation of the permission.
    */
  def encode(p: Permission): Int = p match {
    case Execute => 1
    case Read    => 4
    case Write   => 2
  }

  /**
    * Encode a set of permissions into an integer value.
    *
    * @param ps A set of permissions.
    * @return The integer representation of the permissions.
    */
  def encodeSet(ps: Set[Permission]): Int =
    ps.foldLeft(0)((left: Int, right: Permission) => left | encode(right))

  /**
    * Decode a given integer value into the appropriate permissions.
    *
    * @param e An integer value that holds an encoded permissions.
    * @return A set holding the encoded permissions.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  @throws[IllegalArgumentException]
  def decode(e: Int): Set[Permission] = e match {
    case 0 => Set.empty
    case 1 => Set(Execute)
    case 2 => Set(Write)
    case 3 => Set(Execute, Write)
    case 4 => Set(Read)
    case 5 => Set(Execute, Read)
    case 6 => Set(Read, Write)
    case 7 => Set(Execute, Read, Write)
    case _ => throw new IllegalArgumentException(s"Invalid encoded permission: $e")
  }

  /**
    * Argonaut JSON codec for decoding and encoding permission sets from and to JSON.
    *
    * @return The argonaut codec for json de- and encoding.
    */
  implicit def PermissionSetCodec: CodecJson[Set[Permission]] =
    CodecJson(
      (ps: Set[Permission]) => jNumber(Permission.encodeSet(ps)),
      c => for { pe <- c.as[Int] } yield Permission.decode(pe)
    )

}
