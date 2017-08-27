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

import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.{ Inject, Singleton }

import actors.CronMaster.CronMasterMessages.{ InitializeCrons, UpdateCron }
import actors.WorkQueueMaster.WorkQueueMasterMessages
import akka.actor._
import akka.util.Timeout
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import com.wegtam.tensei.server.ServerMessages
import dao._
import models.WorkQueueEntry
import play.api.Configuration

import scala.concurrent.duration._
import scalaz._

/**
  * This actor manages the cronjobs.
  *
  * @param configuration The current application configuration provided via dependency injection.
  * @param cronDAO The DAO for cronjobs provided via dependency injection.
  * @param transformationConfigurationDAO The DAO for transformation configurations provided via dependency injection.
  */
@Singleton
class CronMaster @Inject()(configuration: Configuration,
                           cronDAO: CronDAO,
                           transformationConfigurationDAO: TransformationConfigurationDAO)
    extends Actor
    with ActorLogging {
  val DEFAULT_TIMEOUT = 5000L // The fallback default timeout in milliseconds.
  val DEFAULT_DELAY   = 500L  // The fallback default initial delay for the cronjob service.

  val scheduler              = QuartzSchedulerExtension(context.system)
  val tcQueueMasterSelection = context.system.actorSelection(s"/user/${WorkQueueMaster.name}")
  implicit val timeout = Timeout(
    FiniteDuration(
      configuration.getMilliseconds("tensei.frontend.ask-timeout").getOrElse(DEFAULT_TIMEOUT),
      MILLISECONDS
    )
  )

  val defaultTimezone = java.util.TimeZone.getTimeZone(ZonedDateTime.now().getZone)

  log.info("CronMaster service started at {}.", self.path)

  import context.dispatcher
  val delay = FiniteDuration(
    configuration.getMilliseconds("tensei.frontend.cronjobs.init-delay").getOrElse(DEFAULT_DELAY),
    MILLISECONDS
  )
  val initTimer = context.system.scheduler.scheduleOnce(delay, self, InitializeCrons)

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    initTimer.cancel()
    super.postStop()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.NonUnitStatements"))
  override def receive: Receive = {
    case InitializeCrons =>
      log.info("Initialising cronjobs...")
      initializeCrons()

    case UpdateCron(id) =>
      log.debug("Update the information about cron with id: {}", id)
      if (id.isValidLong)
        updateCron(id)

    case msg: ServerMessages.StartTransformationConfigurationResponse =>
      msg.statusMessage match {
        case -\/(error) =>
          log.error(error.message)
          error.cause.foreach(c => log.error("Caused by {}", c.message))
        case \/-(status) =>
          log.debug(status)
      }

    case msg =>
      log.warning("Unhandled message to CronMaster from {}", sender().path)
      log.debug("Unhandled message: {}", msg)
  }

  /**
    * Update the cronjob with the given id.
    *
    * @param id The database id of the crontab entry.
    */
  def updateCron(id: Long): Unit = {

    /**
      * Helper function to cancel a cronjob with the given name.
      *
      * @param n The name of the cronjob.
      */
    def deleteCronjob(n: String): Unit = {
      // Cancel the job if it exists.
      scheduler.runningJobs.get(n).foreach { key =>
        if (scheduler.cancelJob(n)) {
          log.debug("Cancelled running cronjob {} ({}).", id, n)
        } else {
          log.warning("Could not cancel running cronjob {} ({})!", id, n)
        }
      }

      // Remove a formerly scheduled job
      if (scheduler.schedules.contains(n.toUpperCase(Locale.ROOT)))
        scheduler.schedules = scheduler.schedules.-(n.toUpperCase(Locale.ROOT))
    }

    val name = s"Cronjob-$id"
    cronDAO
      .findById(id)
      .foreach(
        o =>
          o.fold(deleteCronjob(name)) { cronjob =>
            // Remove from scheduler
            val canceled =
              if (scheduler.runningJobs.contains(name))
                scheduler.cancelJob(name)
              else
                true
            // Add cron to scheduler if: active and has transformation configuration
            if (canceled && cronjob.active) {
              transformationConfigurationDAO
                .findById(cronjob.tkid)
                .foreach(
                  to =>
                    to.fold() {
                      tk =>
                        // Remove a formerly scheduled job.
                        if (scheduler.schedules.contains(name.toUpperCase(Locale.ROOT))) {
                          scheduler.schedules = scheduler.schedules.-(name.toUpperCase(Locale.ROOT))
                        }
                        scheduler.createSchedule(name,
                                                 cronjob.description,
                                                 cronjob.format,
                                                 None,
                                                 defaultTimezone)
                        val entry = WorkQueueEntry.fromCron(cronjob.tkid)
                        tcQueueMasterSelection
                          .resolveOne()
                          .foreach(
                            ref =>
                              scheduler
                                .schedule(name, ref, WorkQueueMasterMessages.AddToQueue(entry))
                          )
                  }
                )
            }
        }
      )
  }

  /**
    * Initialise all cronjobs from the crontab that are marked active.
    */
  def initializeCrons(): Unit =
    cronDAO.allActive.foreach(
      crons =>
        crons.foreach(
          cron =>
            transformationConfigurationDAO
              .findById(cron.tkid)
              .foreach(
                o =>
                  o.fold() { tk =>
                    val name = s"Cronjob-${cron.id}"
                    // Remove a formerly scheduled job
                    if (scheduler.schedules.contains(name.toUpperCase(Locale.ROOT)))
                      scheduler.schedules = scheduler.schedules.-(name.toUpperCase(Locale.ROOT))

                    scheduler
                      .createSchedule(name, cron.description, cron.format, None, defaultTimezone)
                    val entry = WorkQueueEntry.fromCron(cron.tkid)
                    tcQueueMasterSelection
                      .resolveOne()
                      .foreach(
                        ref =>
                          scheduler.schedule(name, ref, WorkQueueMasterMessages.AddToQueue(entry))
                      )
                }
            )
      )
    )
}

object CronMaster {
  def props: Props = Props(classOf[CronMaster])

  sealed trait CronMasterMessages

  object CronMasterMessages {

    final case class UpdateCron(id: Long) extends CronMasterMessages

    case object InitializeCrons extends CronMasterMessages

  }

  val Name = "CronMaster"
}
