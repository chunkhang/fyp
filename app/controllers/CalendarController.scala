package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.mvc._
import actions._

class CalendarController @Inject()(
  cc: ControllerComponents,
  authenticatedAction: AuthenticatedAction,
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def index = authenticatedAction { implicit request =>
    Ok(views.html.calendar.index())
  }

}
