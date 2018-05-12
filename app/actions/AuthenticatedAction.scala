package actions

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import controllers._

class AuthenticatedRequest[A]
  (val name: String, val email: String, val role: String, request: Request[A])
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
      request.session("token")
      val name = request.session("name")
      val email = request.session("email")
      val role = request.session("role")
      block(new AuthenticatedRequest(name, email, role, request))
    } catch {
      case e: NoSuchElementException =>
        Future.successful(
          Results.Redirect(routes.PageController.login())
            .withNewSession
        )
    }
  }

}
