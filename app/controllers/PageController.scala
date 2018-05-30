package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.mvc._
import models._

class PageController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def index = userAction { implicit request =>
    Redirect(routes.CalendarController.index())
  }

  def login = Action { implicit request =>
    request.session.get("email") match {
      case Some(_) =>
        Redirect(routes.PageController.index())
      case None =>
        implicit val mockRequest = new UserRequest[AnyContent](
          User(
            name = "",
            email = "",
            refreshToken = ""
          ),
          request
        )
        Ok(views.html.pages.login())
    }
  }

}
