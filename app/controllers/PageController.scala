package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.mvc._
import actions.AuthenticatedAction

class PageController @Inject()(
  cc: ControllerComponents,
  authenticatedAction: AuthenticatedAction
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def index = authenticatedAction { implicit request =>
    Ok(views.html.index(request.name, request.email))
  }

  def login = Action { implicit request =>
    try {
      request.session("accessToken")
      request.session("name")
      request.session("email")
      Redirect(routes.PageController.index())
    } catch {
      case e: NoSuchElementException =>
        Ok(views.html.login())
    }
  }

}
