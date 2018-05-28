package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.Exception
import play.api.Logger
import play.api.mvc._
import play.api.libs.ws._
import play.api.Configuration
import play.api.http.HeaderNames.AUTHORIZATION
import reactivemongo.bson.BSONObjectID
import models._

class AuthenticationController @Inject()(
  cc: ControllerComponents,
  ws: WSClient,
  config: Configuration,
  userRepo: UserRepository
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  case class ResultCode(code: String)
  case class ResultTokens(accessToken: String, refreshToken: String)
  case class ResultUserInfo(name: String, email: String)
  case class ResultDomain(valid: Boolean)
  case class ResultUser(maybeUser: Option[User])

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
    var invalidDomain = false
    val result = (for {
      // Get code from query params
      r1 <- Future(
        ResultCode(
          code = request.queryString("code").mkString("")
        )
      )
      // POST request to get response with tokens
      r2 <- ws.url(config.get[String]("my.auth.tokenUrl"))
              .addHttpHeaders(
                "Content-Type" -> "application/x-www-form-urlencoded"
              )
              .post(Map(
                "client_id" -> config.get[String]("my.auth.clientId"),
                "client_secret" -> config.get[String]("my.auth.clientSecret"),
                "code" -> r1.code,
                "redirect_uri" -> config.get[String]("my.auth.authUri"),
                "grant_type" -> config.get[String]("my.auth.grantType")
              )).map { response =>
        ResultTokens(
          accessToken = (response.json \ "access_token").as[String],
          refreshToken = (response.json \ "refresh_token").as[String]
        )
      }
      // GET request to get user information
      r3 <- ws.url(config.get[String]("my.api.microsoft.userUrl"))
              .addHttpHeaders(AUTHORIZATION -> s"Bearer ${r2.accessToken}")
              .get().map { response =>
        ResultUserInfo(
          name = (response.json \ "displayName").as[String],
          email = (response.json \ "mail").as[String]
        )
      }
      // Check email domain
      r4 <- Future {
        val valid_ =
          r3.email.endsWith("@" + config.get[String]("my.auth.domain"))
        if (!valid_) invalidDomain = true
        ResultDomain(
          valid = valid_
        )
      }
      // Find user in database
      r5 <-
        if (r4.valid)
          userRepo.findUserByEmail(r3.email).map { maybeUser_ =>
            ResultUser(
              maybeUser = maybeUser_
            )
          }
        else Future.failed(new Exception("invalid domain"))
      // Create or update user in database
      r6 <- Future {
        val latestUser = User(
          name = r3.name,
          email = r3.email,
          refreshToken = r2.refreshToken
        )
        r5.maybeUser match {
          case Some(user) =>
            userRepo.update(user._id.get, latestUser).map { _ =>
              Logger.info(s"Updated User(${r3.email})")
            }
          case None =>
            userRepo.create(latestUser).map { _ =>
              Logger.info(s"Created User(${r3.email})")
            }
        }
      }
    } yield Option(r2, r3, r4))
      .fallbackTo(Future(None))
    result.map { maybeTuple =>
      maybeTuple match {
        case Some(tuple) =>
          val tokens = tuple._1
          val userInfo = tuple._2
          val domain = tuple._3
          // Save session
          Redirect(routes.PageController.index())
            .withSession(
              "accessToken" -> tokens.accessToken,
              "name" -> userInfo.name,
              "email" -> userInfo.email
            )
        case None =>
          if (invalidDomain) {
            Redirect(routes.PageController.login())
              .flashing("error" ->
                s"Required domain: ${config.get[String]("my.auth.domain")}"
              )
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
