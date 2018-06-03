package com.chunkhang.fyp

import java.io.File
import javax.inject.Inject
import play.api.Configuration
import play.api.Logger
import play.api.libs.mailer._
import org.apache.commons.mail.EmailAttachment

class Mailer @Inject()(
  mailerClient: MailerClient,
  config: Configuration
) {

  private def sendEmail(subject: String, to: Seq[String], body: String) = {
    val name = config.get[String]("play.mailer.name")
    val address = s"<${config.get[String]("play.mailer.user")}>"
    val from = s"${name} ${address}"
    val text = Some(body.stripMargin.trim)
    mailerClient.send(Email(subject, from, to, bodyText = text))
    val toAddresses = to.map(address => s"<${address}>").mkString(", ")
    Logger.info(s"Sent email(s) to ${toAddresses}")
  }

  def sendHelloWorld(subject: String, to: Seq[String], name: String) = {
    val body = s"""
      |Hello World, ${name}!
      |This is an email sent from Play Scala.
    """
    sendEmail(subject, to, body)
  }

}

