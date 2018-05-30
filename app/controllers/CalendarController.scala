package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.mvc._
import actions._

class CalendarController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def index = userAction { implicit request =>
    Ok(views.html.calendar.index())
  }

}
