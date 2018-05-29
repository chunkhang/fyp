package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.mvc._
import actions._
import models._

class LecturerController @Inject()(
  cc: ControllerComponents,
  authenticatedAction: AuthenticatedAction,
  userRepo: UserRepository

)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def index = authenticatedAction.async { implicit request =>
    userRepo.readAll().map { users =>
      Ok(views.html.lecturer.index(users))
    }
  }

}
