package mailer

import java.io._
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import play.api.Configuration
import play.api.Logger
import play.api.libs.mailer._
import org.apache.commons.mail.EmailAttachment
import biweekly._
import helpers.Utils

class Mailer @Inject()(
  mailerClient: MailerClient,
  config: Configuration,
  utils: Utils
) {

  private def sendEmail(
    subject: String,
    toList: Seq[String],
    body: String,
    attachmentList: Seq[AttachmentFile] = Seq[AttachmentFile]()
  ): Future[Unit] = Future {
    val name = config.get[String]("play.mailer.name")
    val address = s"<${config.get[String]("play.mailer.user")}>"
    val from = s"${name} ${address}"
    val text = Some(body.stripMargin.trim + "\n")
    mailerClient.send(Email(
      subject,
      from,
      // Only send to this email during development
      Seq(config.get[String]("play.mailer.email.dev")),
      // toList,
      bodyText = text,
      attachments = attachmentList
    ))
    val toAddresses = toList.map(address => s"<${address}>").mkString(", ")
    Logger.info(s"Sent email(s) to ${toAddresses}")
  }

  def sendIcs(
    subject: String,
    toList: Seq[String],
    ics: ICalendar,
    cancel: Boolean = false,
    replace: Boolean = false,
    isTask: Boolean = false,
    cancelledDate: Option[String] = None
  ) = {
    var event = ics.getEvents().get(0)
    val sentence =
      "Please download and open the attached ics file to update your calendar."
    var body = ""
    if (!isTask) {
      if (!cancel) {
        // Added or update class
        body = s"""
        |Dear students,
        |
        |The following class has been added:
        |
        |----------
        |
        |${event.getDescription().getValue()}
        |
        |----------
        |
        |${sentence}
        |
        |Best regards,
        |Class Activity Management System
        """
      } else {
        if (!replace) {
          // Cancelled class
          body = s"""
          |Dear students,
          |
          |The following class has been cancelled on this date:
          |
          |${cancelledDate.get}
          |
          |----------
          |
          |${event.getDescription().getValue()}
          |
          |----------
          |
          |${sentence}
          |
          |Best regards,
          |Class Activity Management System
          """
        } else {
          // Replaced class
          event = ics.getEvents().get(1)
          body = s"""
          |Dear students,
          |
          |The following class has been replaced:
          |
          |----------
          |
          |${event.getDescription().getValue()}
          |
          |----------
          |
          |${sentence}
          |
          |Best regards,
          |Class Activity Management System
          """
        }
      }
    } else {
      // Added task
      body = s"""
      |Dear students,
      |
      |The following task has been added:
      |
      |----------
      |
      |${event.getDescription().getValue()}
      |
      |----------
      |
      |${sentence}
      |
      |Best regards,
      |Class Activity Management System
      """
    }
    val file = new File(s"/tmp/CAMS-${utils.timestampNow()}.ics")
    Biweekly.write(ics).go(file)
    val attachments = Seq(
      AttachmentFile(file.getName(), file)
    )
    sendEmail(subject, toList, body, attachmentList = attachments)
  }

}

