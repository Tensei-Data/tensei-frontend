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

/**
  * A class for the form data of the connection between a dfasdl and a connection
  * information resource.
  *
  * @param dfasdlId The database id of the dfasdl resource.
  * @param connectionInformationId The database id of the connection information resource.
  */
final case class DfasdlConnectionMapping(dfasdlId: Long, connectionInformationId: Long)

object DfasdlConnectionMapping {

  /**
    * The argonaut codec to decode or encode a dfasdl connection mapping from and to json.
    *
    * @return The json codec for decoding and endocing the dfasdl connection mapping.
    */
  implicit def DfasdlConnectionMappingCodec: CodecJson[DfasdlConnectionMapping] =
    CodecJson(
      (m: DfasdlConnectionMapping) =>
        ("dfasdlId" := m.dfasdlId)
        ->: ("connectionInformationId" := m.connectionInformationId) ->: jEmptyObject,
      c =>
        for {
          did <- (c --\ "dfasdlId").as[Long]
          cid <- (c --\ "connectionInformationId").as[Long]
        } yield DfasdlConnectionMapping(dfasdlId = did, connectionInformationId = cid)
    )

}
