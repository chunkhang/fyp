package controllers

import javax.inject._
import play.api.mvc._

class PageController @Inject()(cc: ControllerComponents)
 extends AbstractController(cc) {


  /**
   * The index action.
   */
  def index = Action { implicit request =>
    Ok(views.html.index())
  }

 }