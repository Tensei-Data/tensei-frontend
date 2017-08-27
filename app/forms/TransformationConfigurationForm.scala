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

package forms

import models.DfasdlConnectionMapping
import play.api.data.Form
import play.api.data.Forms._

/**
  * This form handles the creation and editing of triggers.
  */
object TransformationConfigurationForm {

  // A play framework form definition.
  val form = Form(
    mapping(
      "name" -> optional(nonEmptyText).verifying("errors.element.name",
                                                p => p.fold(true)(_.matches("[a-zA-Z0-9-]+"))),
      "cookbookResource" -> longNumber,
      "sources" -> list(
        mapping(
          "dfasdlId"                -> longNumber,
          "connectionInformationId" -> longNumber
        )(DfasdlConnectionMapping.apply)(DfasdlConnectionMapping.unapply)
      ).verifying("errors.tc.nosources", p => p.nonEmpty),
      "target" -> mapping(
        "dfasdlId"                -> longNumber,
        "connectionInformationId" -> longNumber
      )(DfasdlConnectionMapping.apply)(DfasdlConnectionMapping.unapply),
      "authorisation" -> ResourceAuthorisationFieldsForm.form
    )(Data.apply)(Data.unapply)
  )

  // A form for a simple connection mapping.
  val dfasdlConnectionMappingForm = Form(
    mapping(
      "dfasdlId"                -> longNumber,
      "connectionInformationId" -> longNumber
    )(DfasdlConnectionMapping.apply)(DfasdlConnectionMapping.unapply)
  )

  /**
    * A class for the form data.
    *
    * @param name An optional human readable name for the transformation configuration.
    * @param cookbookResourceId The database id of the cookbook resource that shall be used.
    * @param sources A list of mappings matching a dfasdl id to a connection information resource id.
    * @param target The mapping holding the target dfasdl id and the corresponding connection information resource id.
    * @param authorisation The authorisation fields for the resource.
    */
  final case class Data(
      name: Option[String],
      cookbookResourceId: Long,
      sources: List[DfasdlConnectionMapping],
      target: DfasdlConnectionMapping,
      authorisation: ResourceAuthorisationFieldsForm.Data
  )

}
