package controllers

import javax.inject._
import scala.concurrent.ExecutionContext
import play.api.mvc._
import play.api.libs.ws._
import play.api.Configuration
import play.api.http.HeaderNames.AUTHORIZATION

class AuthenticationController @Inject()
  (cc: ControllerComponents, ws: WSClient, config: Configuration)
  (implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def logon = Action { implicit request =>
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
      .withNewSession
  }

  def authenticate = Action.async { implicit request =>
    // Get code from query params
    val code = request.queryString("code").mkString("")
    // POST request to get access token
    ws.url(config.get[String]("my.authorize.accessTokenUrl"))
      .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
      .post(Map(
        "client_id" -> config.get[String]("my.authorize.clientId"),
        "client_secret" -> config.get[String]("my.authorize.clientSecret"),
        "code" -> code,
        "redirect_uri" -> config.get[String]("my.authorize.redirectUri"),
        "grant_type" -> config.get[String]("my.authorize.grantType")
      ))
      .flatMap { postResponse =>
        val token = (postResponse.json \ "access_token").as[String]
        // GET request to get user information
        ws.url(config.get[String]("my.api.userUrl"))
          .addHttpHeaders(AUTHORIZATION -> s"Bearer $token")
          .get()
          .map { getResponse =>
            // Check domain
            val name = (getResponse.json \ "displayName").asOpt[String]
            val email = (getResponse.json \ "mail").asOpt[String]
            val lecturerDomain = "@" + config.get[String]("my.domain.lecturer")
            val studentDomain = "@" + config.get[String]("my.domain.student")
            var role: Option[String] = None
            if (!email.isEmpty) {
              if (email.get.endsWith(lecturerDomain)) {
                role = Option("lecturer")
              } else if (email.get.endsWith(studentDomain)) {
                role = Option("student")
              }
            }
            if (!role.isEmpty) {
              // Save token, email and role
              Redirect(routes.PageController.index())
                .withSession(
                  "token" -> token,
                  "name" -> name.get,
                  "email" -> email.get,
                  "role" -> role.get
                )
            } else {
              Redirect(routes.PageController.login())
                .flashing("error" -> "You must login using an accepted domain")
                .withNewSession
            }
          }
      }
  }

  def logout = Action { implicit request =>
    // Clear session
    Redirect(routes.PageController.index())
      .withNewSession
  }

}
