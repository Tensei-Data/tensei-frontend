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

import scala.collection.immutable.Seq

/**
  * A helper for some simple statistics regarding a DFASDL.
  *
  * @param dataElements A list of entities that describe the data elements that occur in the DFASDL and the related absolute frequency.
  * @param structureElements A list of entities that describe the structure elements that occur in the DFASDL and the related absolute frequency.
  */
final case class DfasdlStatistics(
    dataElements: Seq[ChartDataEntry],
    structureElements: Seq[ChartDataEntry]
)
