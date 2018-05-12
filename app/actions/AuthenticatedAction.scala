package actions

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import controllers._

class AuthenticatedRequest[A]
  (val email: String, val role: String, request: Request[A])
  extends WrappedRequest[A](request)

class AuthenticatedAction @Inject()
  (val parser: BodyParsers.Default)
  (implicit val executionContext: ExecutionContext)
  extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] = {
    // Check token, email and role
    try {
      request.session.get("token").get
      val email = request.session.get("email").get
      val role = request.session.get("role").get
      block(new AuthenticatedRequest(email, role, request))
    } catch {
      case e: NoSuchElementException =>
        Future.successful(
          Results.Redirect(routes.PageController.login())
            .withNewSession
        )
    }
  }

}
