package controllers

import scala.concurrent.{ExecutionContext, Future}
import javax.inject._
import play.api.mvc._
import play.api.libs.ws._
import play.api.Configuration

class AuthorizeController @Inject()
  (cc: ControllerComponents, ws: WSClient, config: Configuration)
  (implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // Redirect to external logon page
  def login = Action { implicit request =>
    val baseUrl =
      "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    val params = Map(
      "client_id" -> config.get[String]("authorize.clientId"),
      "redirect_uri" -> config.get[String]("authorize.redirectUri"),
      "response_type" -> config.get[String]("authorize.responseType"),
      "scope" -> config.get[String]("authorize.scope")
    )
    val query = params.foldLeft("?")( (acc, kv) =>
      acc + "&" + kv._1 + "=" + kv._2
    )
    val logonUrl = baseUrl + query
    Redirect(logonUrl, 302)
  }

  // Get access token
  def access = Action { implicit request =>
    val baseUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    Redirect(baseUrl, 302)
  }

  // Logout
  def logout = Action { implicit request =>
    Redirect(routes.PageController.index())
  }

}
