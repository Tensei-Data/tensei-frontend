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

package actors.websockets

import actors.AgentRunLogsFetcher
import actors.websockets.AgentRunLogUpdate.UpdateSocketOptions
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import dao.AgentRunLogsDAO
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.twirl.api.Html

import scala.collection.immutable.Seq

/**
  * This is a simple websocket actor that will query the database upon request
  * for agent run logs and output them to the given output actor.
  *
  * @param out An actor ref to the actor that will receive the output.
  * @param agentRunLogsDAO The DAO for database access.
  * @param fetcher The actor ref of the log data fetcher.
  */
class AgentRunLogUpdate(out: ActorRef, agentRunLogsDAO: AgentRunLogsDAO, fetcher: ActorRef)
    extends Actor
    with ActorLogging {

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.NonUnitStatements"))
  override def receive: Receive = {
    case UpdateSocketOptions(uuid, offset) =>
      log.debug("Received log page update request on {} for offset: {}", uuid, offset.getOrElse(0))
      // Instruct the fetcher to fetch some logs.
      fetcher ! AgentRunLogsFetcher.FetchLogs(uuid, offset)
      // We need to trim the output string to avoid sending messages of whitespace!
      agentRunLogsDAO.takeAllFromOffset(uuid, offset).foreach { ls =>
        val renderedHtml: Html = views.html.dashboard.agentRunLogs.logLines(ls)
        val websocketOutput: JsValue = JsObject(
          Seq(
            "html" -> JsString(renderedHtml.toString().trim)
          )
        )
        out ! websocketOutput
      }
  }

}

object AgentRunLogUpdate {

  /**
    * A factory method to create the agent run log update websocket actor.
    *
    * @param outputActorRef An actor ref to the actor that will receive the output.
    * @param agentRunLogsDAO The DAO for database access.
    * @param fetcher The actor ref of the log data fetcher.
    * @return The props to create the actor.
    */
  def props(outputActorRef: ActorRef, agentRunLogsDAO: AgentRunLogsDAO, fetcher: ActorRef): Props =
    Props(new AgentRunLogUpdate(outputActorRef, agentRunLogsDAO, fetcher))

  /**
    * The options for the request to the websocket that updates the logs on the page.
    *
    * @param uuid The id of the related transformation queue history entry.
    * @param offset An option to the last offet of the last log entry.
    */
  final case class UpdateSocketOptions(uuid: String, offset: Option[Long])

  // Use the play json auto format macro.
  implicit val updateSocketOptionsFormat: OFormat[UpdateSocketOptions] =
    Json.format[UpdateSocketOptions]

}
