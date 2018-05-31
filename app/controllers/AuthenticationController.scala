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
    val codeSequence = request.queryString.get("code")
    if (!codeSequence.isEmpty) {
      val code = codeSequence.get.mkString("")
      processCode(code).flatMap { result =>
        val (accessToken, refreshToken, name, email) = result
        // Check email domain
        if (email.endsWith("@" + config.get[String]("my.domain.lecturer"))) {
          saveUser(name, email, refreshToken).map { _ =>
            // Save session
            Redirect(routes.PageController.index())
              .withSession(
                "name" -> name,
                "email" -> email
              )
          } recover {
            case _ =>
              Redirect(routes.PageController.login())
          }
        } else {
          Future {
            Redirect(routes.PageController.login())
              .flashing("danger" ->
                s"Required domain: ${config.get[String]("my.domain.lecturer")}"
              )
          }
        }
      } recover {
        case _ =>
          Redirect(routes.PageController.login())
      }
    } else {
      Future(Redirect(routes.PageController.login()))
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

  // Process code to obtain tokens and user information
  def processCode(code: String): Future[(String, String, String, String)] = {
    for {
      // POST request to get response with tokens
      tokens <- ws.url(config.get[String]("my.auth.tokenUrl"))
              .addHttpHeaders(
                "Content-Type" -> "application/x-www-form-urlencoded"
              )
              .post(Map(
                "client_id" -> config.get[String]("my.auth.clientId"),
                "client_secret" -> config.get[String]("my.auth.clientSecret"),
                "code" -> code,
                "redirect_uri" -> config.get[String]("my.auth.authUri"),
                "grant_type" -> config.get[String]("my.auth.grantType")
              )).map { response =>
        val accessToken = (response.json \ "access_token").as[String]
        val refreshToken = (response.json \ "refresh_token").as[String]
        (accessToken, refreshToken)
      }
      // GET request to get user information
      userInfo <- ws.url(config.get[String]("my.api.microsoft.userUrl"))
              .addHttpHeaders(AUTHORIZATION -> s"Bearer ${tokens._1}")
              .get().map { response =>
        val name = (response.json \ "displayName").as[String]
        val email = (response.json \ "mail").as[String]
        (name, email)
      }
    } yield (tokens._1, tokens._2, userInfo._1, userInfo._2)
  }

  // Save user in database
  def saveUser(
    userName: String,
    userEmail: String,
    userRefreshToken: String
  ): Future[Unit] = {
    for {
      // Find user in database
      maybeUser <- userRepo.findUserByEmail(userEmail).map { maybeUser =>
        maybeUser
      }
      // Create or update user in database
      _ <- {
        val latestUser = User(
          name = userName,
          email = userEmail,
          refreshToken = userRefreshToken
        )
        maybeUser match {
          case Some(user) =>
            userRepo.update(user._id.get, latestUser).map { _ =>
              Logger.info(s"Updated User(${userEmail})")
            }
          case None =>
            userRepo.create(latestUser).map { _ =>
              Logger.info(s"Created User(${userEmail})")
            }
        }
      }
    } yield Unit
  }

}

class UserRequest[A](
  val user: User,
  request: Request[A]
) extends WrappedRequest[A](request)

class UserAction @Inject()(
  val parser: BodyParsers.Default,
  userRepo: UserRepository
)(
  implicit val executionContext: ExecutionContext
) extends ActionBuilder[UserRequest, AnyContent] {

  def invokeBlock[A](
    request: Request[A],
    block: UserRequest[A] => Future[Result]
  ): Future[Result] = {
    // Check session
    request.session.get("email") match {
      case Some(email) =>
        // Check email
        userRepo.findUserByEmail(email).flatMap { maybeUser =>
          maybeUser match {
            case Some(user) =>
              block(new UserRequest(user, request))
            case None =>
              Future {
                Results.Redirect(routes.PageController.login())
                  .withNewSession
              }
          }
        }
      case None =>
        Future {
          Results.Redirect(routes.PageController.login())
            .withNewSession
        }
    }
  }

}
