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

class Mailer @Inject()(
  mailerClient: MailerClient,
  config: Configuration
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
    filename: String
  ) = {
    val body = s"""
      |Hello!
      |This is an email sent from Play Scala.
    """
    val file = new File(s"/tmp/${filename}")
    Biweekly.write(ics).go(file)
    val attachments = Seq(
      AttachmentFile(file.getName(), file)
    )
    sendEmail(subject, toList, body, attachmentList = attachments)
  }

}

