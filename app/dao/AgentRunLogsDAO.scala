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

import models.AgentRunLogLine
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import slick.lifted.ProvenShape

import scala.concurrent.Future
import scala.util.Try

class AgentRunLogsDAO @Inject()(override protected val configuration: Configuration,
                                override protected val dbConfigProvider: DatabaseConfigProvider)
    extends Tables(configuration, dbConfigProvider) {
  import driver.api._

  /**
    * A slick table definition for the database table that holds the log entries.
    *
    */
  class AgentRunLogs(tag: Tag) extends Table[AgentRunLogLine](tag, "agent_run_logs") {
    def uuid   = column[String]("uuid", O.Length(64, varying = true))
    def offset = column[Long]("line_offset")
    def line   = column[String]("line")

    def pk = primaryKey("agent_run_logs_pk", (uuid, offset))
    def fk =
      foreignKey("agent_run_logs_fk", uuid, workHistory)(_.uuid,
                                                         onDelete = ForeignKeyAction.Cascade,
                                                         onUpdate = ForeignKeyAction.Cascade)

    override def * : ProvenShape[AgentRunLogLine] =
      (uuid, offset, line).shaped <> ((AgentRunLogLine.apply _).tupled, AgentRunLogLine.unapply)

  }
  lazy val agentRunLogs = TableQuery[AgentRunLogs]

  /**
    * Return all log lines for the given id. The lines will be sorted by their
    * line offset in ascending order. Optional the number entries to skip and
    * take (e.g. `LIMIT`) can be specified.
    *
    * @param uuid The id of the related transformation queue history entry.
    * @param skip An option to the number of entries that should be omitted (starting from the first).
    * @param take An option to the maximum number of entries to return.
    * @return A future holding a sequence of log lines.
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def all(uuid: String,
          skip: Option[Int] = None,
          take: Option[Int] = None): Future[Seq[AgentRunLogLine]] = {
    val select = agentRunLogs.filter(_.uuid === uuid).sortBy(_.offset.asc).drop(skip.getOrElse(0))
    dbConfig.db.run(take.map(t => select.take(t)).getOrElse(select).result)
  }

  /**
    * Destroy all log lines for the given id in the database table.
    *
    * @param uuid The id of the related transformation queue history entry.
    * @return A future holding the number of deleted lines.
    */
  def destroy(uuid: String): Future[Int] =
    dbConfig.db.run(agentRunLogs.filter(_.uuid === uuid).delete)

  /**
    * Destroy all log lines that match the given id and line offset in the database table.
    *
    * @param uuid The id of the related transformation queue history entry.
    * @param offset The log line offset (from the log file).
    * @return A future holding the number of deleted lines.
    */
  def destroy(uuid: String, offset: Long): Future[Int] =
    dbConfig.db.run(agentRunLogs.filter(r => r.uuid === uuid && r.offset === offset).delete)

  /**
    * Check wether any log lines are stored for the given id.
    *
    * @param uuid The id of the related transformation queue history entry.
    * @return A future holding either `true` or `false`.
    */
  def exists(uuid: String): Future[Boolean] =
    dbConfig.db.run(agentRunLogs.filter(_.uuid === uuid).exists.result)

  /**
    * Return the maximum saved log offset for the given id.
    *
    * @param uuid The id of the related transformation queue history entry.
    * @return A future holding an option to the offset.
    */
  def getMaxOffset(uuid: String): Future[Option[Long]] =
    dbConfig.db.run(agentRunLogs.filter(_.uuid === uuid).map(_.offset).max.result)

  /**
    * Insert the given log line into the database table and return the written entry.
    *
    * @param l A log line.
    * @return A future holding the written log line.
    */
  def insert(l: AgentRunLogLine): Future[Try[AgentRunLogLine]] =
    dbConfig.db.run(
      (agentRunLogs += l)
        .andThen(agentRunLogs.filter(r => r.uuid === l.uuid && r.offset === l.offset).result.head)
        .asTry
    )

  /**
    * Insert a list of log lines into the database table and return the written entries.
    *
    * @param ls A list of log lines.
    * @return A future holding the list of written log lines.
    */
  def insert(ls: Seq[AgentRunLogLine]): Future[Try[Seq[AgentRunLogLine]]] = {
    val uuids   = ls.map(_.uuid)
    val offsets = ls.map(_.offset)
    dbConfig.db.run(
      (agentRunLogs ++= ls)
        .andThen(
          agentRunLogs.filter(r => r.uuid.inSetBind(uuids) && r.offset.inSetBind(offsets)).result
        )
        .asTry
    )
  }

  /**
    * Check if the given logline already exists in the database.
    * This function can be used to check for possible duplicates before an insert operation.
    * A threshold value for an offset is calculated based upon the lines offset and length.
    * If another line has an offset equal or larger than the calculated offset and matches
    * the actual logline plus the uuid then the given logline is considered to be already
    * existing within the database table.
    *
    * @param l A log line.
    * @return A future holding either `true` or `false`.
    */
  def loglineExists(l: AgentRunLogLine): Future[Boolean] = {
    val possibleDuplicateOffset = l.offset - l.line.getBytes("UTF-8").length
    dbConfig.db.run(
      agentRunLogs
        .filter(r => r.uuid === l.uuid && r.line === l.line && r.offset >= possibleDuplicateOffset)
        .exists
        .result
    )
  }

  /**
    * Return all log entries for the given id beginning after an optional given offset.
    *
    * @param uuid The id of the related transformation queue history entry.
    * @param offset An optional offset.
    * @return A future holding a list of log lines.
    */
  def takeAllFromOffset(uuid: String, offset: Option[Long]): Future[Seq[AgentRunLogLine]] =
    dbConfig.db.run(
      offset
        .map(o => agentRunLogs.filter(r => r.uuid === uuid && r.offset > o))
        .getOrElse(agentRunLogs.filter(_.uuid === uuid))
        .sortBy(_.offset.asc)
        .result
    )

}
