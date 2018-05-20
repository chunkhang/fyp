package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.async.Async.{async, await}
import play.api.Logger
import play.api.mvc._
import play.api.libs.ws._
import play.api.Configuration
import play.api.http.HeaderNames.AUTHORIZATION
import models.{User, UserRepository}

class AuthenticationController @Inject()(
  cc: ControllerComponents,
  ws: WSClient,
  config: Configuration,
  userRepo: UserRepository
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def logon = Action { implicit request =>
    // GET request to logon page
    val logonUrl = urlWithParams(config.get[String]("my.auth.logonUrl"),
      Map(
        "client_id" -> config.get[String]("my.auth.clientId"),
        "redirect_uri" -> config.get[String]("my.auth.authUri"),
        "response_type" -> config.get[String]("my.auth.responseType"),
        "scope" -> config.get[String]("my.auth.scope"),
        "prompt" -> config.get[String]("my.auth.prompt")
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
      ws.url(config.get[String]("my.auth.tokenUrl"))
        .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .post(Map(
          "client_id" -> config.get[String]("my.auth.clientId"),
          "client_secret" -> config.get[String]("my.auth.clientSecret"),
          "code" -> code,
          "redirect_uri" -> config.get[String]("my.auth.authUri"),
          "grant_type" -> config.get[String]("my.auth.grantType")
        ))
    }
    // GET request to get user information
    def getUserResponse(accessToken: String) = {
      ws.url(config.get[String]("my.api.userUrl"))
        .addHttpHeaders(AUTHORIZATION -> s"Bearer $accessToken")
        .get()
    }
    // Check email domain
    def validDomain(email: String) = {
      email.endsWith("@" + config.get[String]("my.auth.domain"))
    }
    async {
      var authenticated = false
      var wrongDomain = false
      var code: Option[String] = None
      var tokenResponse: Option[WSResponse] = None
      var accessToken: Option[String] = None
      var refreshToken: Option[String] = None
      var userResponse: Option[WSResponse] = None
      var email: Option[String] = None
      var name: Option[String] = None
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
        name = (userResponse.get.json \ "displayName").asOpt[String]
        email = (userResponse.get.json \ "mail").asOpt[String]
      }
      if (!email.isEmpty && !name.isEmpty) {
        if (validDomain(email.get)) {
          authenticated = true
        } else {
          wrongDomain = true
        }
      }
      if (authenticated) {
        // Create or update user in database
        val latestUser = User(
          name = name.get,
          email = email.get,
          refreshToken = refreshToken.get
        )
        await(userRepo.findByEmail(email.get)) match {
          case Some(user) =>
            userRepo.update(user._id.get, latestUser)
            Logger.info(s"Updated User(${email.get})")
          case None =>
            userRepo.create(latestUser)
            Logger.info(s"Created User(${email.get})")
        }
        // Save session
        Redirect(routes.PageController.index())
          .withSession(
            "accessToken" -> accessToken.get,
            "name" -> name.get,
            "email" -> email.get
          )
      } else {
        if (wrongDomain) {
          Redirect(routes.PageController.login())
            .flashing("error" ->
              s"Required domain: ${config.get[String]("my.auth.domain")}")
        } else {
          Redirect(routes.PageController.login())
        }
      }
    }
  }

  def logout = Action { implicit request =>
    // Clear session
    Redirect(routes.PageController.login())
      .withNewSession
  }

  def urlWithParams(url: String, params: Map[String, String]) = {
    url + "?" + params.foldLeft("")( (acc, kv) =>
      acc + "&" + kv._1 + "=" + kv._2
    ).substring(1)
  }

}
