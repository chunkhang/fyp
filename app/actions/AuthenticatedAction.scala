package actions

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import controllers._
import models._

class AuthenticatedRequest[A](
  val name: String,
  val email: String,
  request: Request[A]
) extends WrappedRequest[A](request)

class AuthenticatedAction @Inject()(
  val parser: BodyParsers.Default,
  userRepo: UserRepository
)(
  implicit val executionContext: ExecutionContext
) extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] = {
    // Check session
    var name: String = ""
    var email: String = ""
    try {
      request.session("accessToken")
      name = request.session("name")
      email = request.session("email")
    } catch {
      case e: NoSuchElementException =>
        Future {
          Results.Redirect(routes.PageController.login())
            .withNewSession
        }
    }
    // Check email
    userRepo.findUserByEmail(email).flatMap { maybeUser =>
      maybeUser match {
        case Some(user) =>
          block(new AuthenticatedRequest(name, email, request))
        case None =>
          Future {
            Results.Redirect(routes.PageController.login())
              .withNewSession
          }
      }
    }
  }

}
