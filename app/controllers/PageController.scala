package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.mvc._
import actions._

class PageController @Inject()(
  cc: ControllerComponents,
  authenticatedAction: AuthenticatedAction
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def index = authenticatedAction { implicit request =>
    Redirect(routes.CalendarController.index())
  }

  def login = Action { implicit request_ =>
    try {
      request_.session("accessToken")
      request_.session("name")
      request_.session("email")
      Redirect(routes.PageController.index())
    } catch {
      case e: NoSuchElementException =>
        // Implicit session for login template
        implicit val session = new AuthenticatedRequest[AnyContent](
          name = "",
          email = "",
          request = request_
        )
        Ok(views.html.pages.login())
    }
  }

}
