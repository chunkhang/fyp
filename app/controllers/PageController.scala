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
            Ok(views.html.index())
          case UNAUTHORIZED =>
            Redirect(routes.AuthorizeController.login())
              .discardingCookies(DiscardingCookie(
                config.get[String]("my.cookie.accessToken")))
        }
      }
  }

}
