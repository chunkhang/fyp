package controllers

import javax.inject._
import scala.concurrent.ExecutionContext
import play.api.mvc._
import play.api.libs.ws._
import play.api.Configuration

class AuthorizeController @Inject()
  (cc: ControllerComponents, ws: WSClient, config: Configuration)
  (implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def login = Action { implicit request =>
    // GET request to logon page
    val params = Map(
      "client_id" -> config.get[String]("my.authorize.clientId"),
      "redirect_uri" -> config.get[String]("my.authorize.redirectUri"),
      "response_type" -> config.get[String]("my.authorize.responseType"),
      "scope" -> config.get[String]("my.authorize.scope")
    )
    val query = params.foldLeft("?")( (acc, kv) =>
      acc + "&" + kv._1 + "=" + kv._2
    )
    val url = config.get[String]("my.authorize.logonUrl") + query
    Redirect(url, 302)
  }

  def access = Action.async { implicit request =>
    // Get code from query params
    val code = request.queryString("code").mkString("")
    // POST request to get token
    ws.url(config.get[String]("my.authorize.accessTokenUrl"))
      .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
      .post(Map(
        "client_id" -> config.get[String]("my.authorize.clientId"),
        "client_secret" -> config.get[String]("my.authorize.clientSecret"),
        "code" -> code,
        "redirect_uri" -> config.get[String]("my.authorize.redirectUri"),
        "grant_type" -> config.get[String]("my.authorize.grantType")
      ))
      .map { response =>
        // Save token as cookie
        val token = (response.json \ "access_token").as[String]
        Redirect(routes.PageController.index())
          .withCookies(Cookie(
            config.get[String]("my.cookie.accessToken"), token,
            Option(config.get[Int]("my.cookie.expiry"))))
          .bakeCookies()
      }
  }

  def logout = Action { implicit request =>
    // Remove token cookie
    Ok("Logout")
      .discardingCookies(DiscardingCookie(
        config.get[String]("my.cookie.accessToken")))
  }

}
