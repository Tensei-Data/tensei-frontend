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

package filters

import javax.inject.Inject

import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import play.filters.cors.CORSFilter
import play.filters.csrf.CSRFFilter

/**
  * A class that is used to provid filters for http requests.
  * For details consult the documentation at https://www.playframework.com/documentation/2.5.x/ScalaCsrf
  *
  * @param corsFilter A filter that implements Cross-Origin Resource Sharing (CORS).
  * @param csrfFilter A filter to provide global CSRF protection.
  */
class CustomFilters @Inject()(corsFilter: CORSFilter, csrfFilter: CSRFFilter) extends HttpFilters {

  override def filters: Seq[EssentialFilter] = Seq(corsFilter, csrfFilter)

}
