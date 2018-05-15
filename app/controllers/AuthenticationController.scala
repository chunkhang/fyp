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
    val logonUrl = urlWithParams(config.get[String]("my.authorize.logonUrl"),
      Map(
        "client_id" -> config.get[String]("my.authorize.clientId"),
        "redirect_uri" -> config.get[String]("my.authorize.authUri"),
        "response_type" -> config.get[String]("my.authorize.responseType"),
        "scope" -> config.get[String]("my.authorize.scope")
      ))
    Redirect(logonUrl)
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
    def getTokenResponse(code: String) = {
      ws.url(config.get[String]("my.authorize.tokenUrl"))
        .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .post(Map(
          "client_id" -> config.get[String]("my.authorize.clientId"),
          "client_secret" -> config.get[String]("my.authorize.clientSecret"),
          "code" -> code,
          "redirect_uri" -> config.get[String]("my.authorize.authUri"),
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
    async {
      var successful = false
      var code: Option[String] = None
      var tokenResponse: Option[WSResponse] = None
      var accessToken: Option[String] = None
      var refreshToken: Option[String] = None
      var userResponse: Option[WSResponse] = None
      var email: Option[String] = None
      var name: Option[String] = None
      var role: Option[String] = None
      code = getCode()
      if (!code.isEmpty) {
        tokenResponse = Option(await(getTokenResponse(code.get)))
      }
      if (!tokenResponse.isEmpty && tokenResponse.get.status == OK) {
        accessToken = (tokenResponse.get.json \ "access_token").asOpt[String]
        refreshToken = (tokenResponse.get.json \ "refresh_token").asOpt[String]
      }
      if (!accessToken.isEmpty && !refreshToken.isEmpty) {
        userResponse = Option(await(getUserResponse(accessToken.get)))
      }
      if (!userResponse.isEmpty && userResponse.get.status == OK) {
        email = (userResponse.get.json \ "mail").asOpt[String]
        name = (userResponse.get.json \ "displayName").asOpt[String]
      }
      if (!email.isEmpty) {
        role = getRole(email.get)
      }
      if (!role.isEmpty) {
        successful = true
      }
      if (successful) {
        // TODO: Save refresh token to database
        // Save access token, email and role to session
        Redirect(routes.PageController.index())
          .withSession(
            "accessToken" -> accessToken.get,
            "name" -> name.get,
            "email" -> email.get,
            "role" -> role.get
          )
      } else {
        Redirect(routes.PageController.login())
      }
    }
  }

  def logout = Action { implicit request =>
    // Clear session
    val logoutUrl = urlWithParams(config.get[String]("my.authorize.logoutUrl"),
      Map(
        "post_logout_redirect_uri" ->
          config.get[String]("my.authorize.loginUri")
      ))
    Redirect(logoutUrl)
      .withNewSession
  }

  def urlWithParams(url: String, params: Map[String, String]) = {
     url + params.foldLeft("?")( (acc, kv) =>
      acc + "&" + kv._1 + "=" + kv._2
    )
  }

}
