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

package actors

import javax.inject.{ Inject, Singleton }

import actors.AgentRunLogsFetcher.FetchLogs
import akka.actor.{ Actor, ActorLogging, Props }
import com.wegtam.tensei.adt.GlobalMessages
import dao.AgentRunLogsDAO
import models.AgentRunLogLine
import play.api.Configuration

import scala.concurrent.Future

@Singleton
class AgentRunLogsFetcher @Inject()(val agentRunLogsDAO: AgentRunLogsDAO,
                                    configuration: Configuration)
    extends Actor
    with ActorLogging {
  import context.dispatcher

  private val frontendSelection = context.system.actorSelection(s"/user/${FrontendService.name}")

  private val maxSize = configuration.getLong("tensei.frontend.log-fetcher-max-bytes")

  // A buffer for the requested offsets.
  val offsets = scala.collection.mutable.Map.empty[String, Long]

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.NonUnitStatements"))
  override def receive: Receive = {
    case FetchLogs(uuid, offset) =>
      log.debug("Fetching log meta data for {}.", uuid)
      offset.foreach(o => offsets.put(uuid, o))
      frontendSelection ! GlobalMessages.RequestAgentRunLogsMetaData(uuid)

    case GlobalMessages.ReportAgentRunLogsMetaData(agentId, uuid, size) =>
      log.debug("Got meta data from logs for {}. Estimated size {} bytes.", uuid, size)
      frontendSelection ! GlobalMessages.RequestAgentRunLogs(
        agentId = agentId,
        uuid = uuid,
        offset = offsets.get(uuid),
        maxSize = maxSize
      )
      val _ = offsets.remove(uuid)

    case GlobalMessages.ReportAgentRunLogLine(uuid, line, offset) =>
      val logLine = AgentRunLogLine(
        uuid = uuid,
        offset = offset,
        line = line
      )
      // TODO This is currently a workaround for loglines returned multiple times. This should be fixed in the logic.
      val result = for {
        exists <- agentRunLogsDAO.loglineExists(logLine)
        insert <- if (exists)
          Future.successful(
            scala.util.Failure(new Error(s"Logline already exists in database: $logLine"))
          )
        else agentRunLogsDAO.insert(logLine)
      } yield insert
      result.foreach {
        case scala.util.Failure(e) => log.error(e, "Could not save logline!")
        case scala.util.Success(_) => log.debug("Saved logline.")
      }
  }

}

object AgentRunLogsFetcher {

  def props: Props = Props(classOf[AgentRunLogsFetcher])

  /**
    * Instruct the actor to fetch the logs for the given uuid.
    *
    * @param uuid The id of a transformation configuration run.
    * @param offset An optional offset.
    */
  final case class FetchLogs(uuid: String, offset: Option[Long])

}
