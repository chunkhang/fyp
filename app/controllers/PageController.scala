package controllers

import javax.inject._
import scala.concurrent.ExecutionContext
import play.api.mvc._
import play.api.libs.ws._
import play.api.Configuration
import play.api.http.HeaderNames.AUTHORIZATION

class PageController @Inject()
  (cc: ControllerComponents, ws: WSClient, config: Configuration)
  (implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def index = Action.async { implicit request =>
    // Get access token
    val token = request.cookies.get("token") match {
      case Some(cookie) => cookie.value
      case None => ""
    }
    // GET request to validate token
    ws.url(config.get[String]("my.api.userUrl"))
      .addHttpHeaders(AUTHORIZATION -> s"Bearer $token")
      .get()
      .map { response =>
        response.status match {
          case OK =>
            // Get email and role
            // Check domain
            val email = (response.json \ "userPrincipalName").as[String]
            val lecturerDomain = "@" + config.get[String]("my.domain.lecturer")
            val studentDomain = "@" + config.get[String]("my.domain.student")
            var role = ""
            var domainIsGood = true
            if (email.endsWith(lecturerDomain)) {
              role = "lecturer"
            } else if (email.endsWith(studentDomain)) {
              role = "student"
            } else {
              domainIsGood = false
            }
            if (domainIsGood) {
              Ok(views.html.index())
                .withSession(
                  "email" -> email,
                  "role" -> role
                )
            } else {
              Ok(views.html.login()(Flash(Map("error" -> "Bad domain"))))
                .discardingCookies(DiscardingCookie(
                  config.get[String]("my.cookie.accessToken")))
            }
          case UNAUTHORIZED =>
            Ok(views.html.login())
              .discardingCookies(DiscardingCookie(
                config.get[String]("my.cookie.accessToken")))
        }
      }
  }

}
