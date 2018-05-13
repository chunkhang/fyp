package controllers

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.async.Async.{async, await}
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
  }

  def authenticate = Action.async { implicit request =>
    // Get code from query params
    def getCode(): Option[String] = {
      request.queryString.get("code") match {
        case Some(value) => Option(value.mkString(""))
        case None => None
      }
    }
    // POST request to get response with tokens
    def getTokensResponse(code: String) = {
      ws.url(config.get[String]("my.authorize.tokenUrl"))
        .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .post(Map(
          "client_id" -> config.get[String]("my.authorize.clientId"),
          "client_secret" -> config.get[String]("my.authorize.clientSecret"),
          "code" -> code,
          "redirect_uri" -> config.get[String]("my.authorize.redirectUri"),
          "grant_type" -> config.get[String]("my.authorize.grantType")
        ))
    }
    // GET request to get user information
    def getUserResponse(accessToken: String) = {
      ws.url(config.get[String]("my.api.userUrl"))
        .addHttpHeaders(AUTHORIZATION -> s"Bearer $accessToken")
        .get()
    }
    // Check domain of email to determine role
    def getRole(email: String): Option[String] = {
      val lecturerDomain = "@" + config.get[String]("my.domain.lecturer")
      val studentDomain = "@" + config.get[String]("my.domain.student")
      email match {
        case a if a.endsWith(lecturerDomain) => Option("lecturer")
        case b if b.endsWith(studentDomain) => Option("student")
        case _ => None
      }
    }
    // Bad authentication
    def redirectToLogin() = {
      Redirect(routes.PageController.login())
    }
    async {
      val code = getCode()
      if (!code.isEmpty) {
        var response = await(getTokensResponse(code.get))
        if (response.status == OK) {
          val accessToken = (response.json \ "access_token").asOpt[String]
          val refreshToken = (response.json \ "refresh_token").asOpt[String]
          if (!accessToken.isEmpty && !refreshToken.isEmpty) {
            response = await(getUserResponse(accessToken.get))
            if (response.status == OK) {
              val email = (response.json \ "mail").asOpt[String]
              val name = (response.json \ "displayName").asOpt[String]
              if (!email.isEmpty) {
                val role = getRole(email.get)
                if (!role.isEmpty) {
                  // TODO: Save refresh token to database
                  // Save access token, email and role to session
                  Redirect(routes.PageController.index())
                    .withSession(
                      "accessToken" -> accessToken.get,
                      "name" -> name.get,
                      "email" -> email.get,
                      "role" -> role.get
                    )
                } else redirectToLogin()
              } else redirectToLogin()
            } else redirectToLogin()
          } else redirectToLogin()
        } else redirectToLogin()
      } else redirectToLogin()
    }
  }

  def logout = Action { implicit request =>
    // Clear session
    Redirect(routes.PageController.index())
      .withNewSession
  }

}
