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
    val params = Map(
      "client_id" -> config.get[String]("authorize.clientId"),
      "redirect_uri" -> config.get[String]("authorize.redirectUri"),
      "response_type" -> config.get[String]("authorize.responseType"),
      "scope" -> config.get[String]("authorize.scope")
    )
    val query = params.foldLeft("?")( (acc, kv) =>
      acc + "&" + kv._1 + "=" + kv._2
    )
    val url = config.get[String]("authorize.logonUrl") + query
    Redirect(url, 302)
  }

  // Get access token
  def access = Action.async { implicit request =>
    val code = request.queryString("code").mkString("")
    val futureResponse = ws.url(config.get[String]("authorize.accessTokenUrl"))
      .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
      .post(Map(
        "client_id" -> config.get[String]("authorize.clientId"),
        "client_secret" -> config.get[String]("authorize.clientSecret"),
        "code" -> code,
        "redirect_uri" -> config.get[String]("authorize.redirectUri"),
        "grant_type" -> config.get[String]("authorize.grantType")
      ))
      futureResponse.map { response =>
        val token = (response.json \ "access_token").as[String]
        println(token)
        Ok(token)
      }
  }

  // Logout
  def logout = Action { implicit request =>
    Ok("Logout")
  }

}
