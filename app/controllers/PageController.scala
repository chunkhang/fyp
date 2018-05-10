package controllers

import javax.inject._
import play.api.mvc._

class PageController @Inject()
  (cc: ControllerComponents)
  extends AbstractController(cc) {

  // Home page
  def index = Action { implicit request =>
    // Check session cookie
    val cookie = false
    if (cookie) {
      Ok(views.html.index())
    } else {
      Redirect(routes.AuthorizeController.login())
    }
  }

}
