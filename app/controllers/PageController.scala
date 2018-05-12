package controllers

import javax.inject._
import scala.concurrent.ExecutionContext
import play.api.mvc._
import actions.AuthenticatedAction

class PageController @Inject()
  (cc: ControllerComponents, authenticatedAction: AuthenticatedAction)
  (implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def index = authenticatedAction { implicit request =>
    // Home page
    Ok(views.html.index())
  }

  def login = Action { implicit request =>
    // Login page
    try {
      request.session.get("token").get
      request.session.get("email").get
      request.session.get("role").get
      Redirect(routes.PageController.index())
    } catch {
      case e: NoSuchElementException =>
        Ok(views.html.login())
    }
  }

}
