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

import models.Permission.{ Execute, Read, Write }
import org.scalatestplus.play.PlaySpec

class PermissionTest extends PlaySpec {
  "Permissions" must {
    "have default group permission set to read and write" in {
      Permission.DEFAULT_GROUP_PERMISSIONS must be(Set(Read, Write))
    }

    "have default world permissions set to read only" in {
      Permission.DEFAULT_WORLD_PERMISSIONS must be(Set(Read))
    }

    "include executable permission in every ExecutablePermissionSets helper" in {
      Permission.ExecutablePermissionSets.foreach(ps => ps must contain(Execute))
    }

    "include read permission in every ReadablePermissionSets helper" in {
      Permission.ReadablePermissionSets.foreach(ps => ps must contain(Read))
    }

    "include write permission in every WritablePermissionSets helper" in {
      Permission.WritablePermissionSets.foreach(ps => ps must contain(Write))
    }

    "encode a single permission into proper unix bits" in {
      Permission.encode(Execute) must be(1)
      Permission.encode(Read) must be(4)
      Permission.encode(Write) must be(2)
    }

    "encode a set of permissions into proper unix bits" in {
      Permission.encodeSet(Set(Execute)) must be(1)
      Permission.encodeSet(Set(Execute, Read)) must be(5)
      Permission.encodeSet(Set(Execute, Read, Write)) must be(7)
      Permission.encodeSet(Set(Execute, Write)) must be(3)
      Permission.encodeSet(Set(Read)) must be(4)
      Permission.encodeSet(Set(Read, Write)) must be(6)
      Permission.encodeSet(Set(Write)) must be(2)
    }

    "encode an empty set to 0" in {
      Permission.encodeSet(Set()) must be(0)
    }

    "decode unix bits into permission sets" in {
      Permission.decode(1) must be(Set(Execute))
      Permission.decode(2) must be(Set(Write))
      Permission.decode(3) must be(Set(Execute, Write))
      Permission.decode(4) must be(Set(Read))
      Permission.decode(5) must be(Set(Execute, Read))
      Permission.decode(6) must be(Set(Read, Write))
      Permission.decode(7) must be(Set(Execute, Read, Write))
    }

    "decode 0 to an an empty permission set" in {
      Permission.decode(0) must be(Set())
    }

    "throw an exception given invalid unix bits for decoding" in {
      an[IllegalArgumentException] mustBe thrownBy(Permission.decode(-1))
    }
  }
}
