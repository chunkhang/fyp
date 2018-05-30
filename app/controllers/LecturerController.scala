package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import actions._
import models._

class LecturerController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  userRepo: UserRepository

)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def index = userAction.async { implicit request =>
    getLecturers().map { lecturers =>
      Ok(views.html.lecturers.index(lecturers))
    }
  }

  // Get saved lecturers from database
  def getLecturers(): Future[List[User]] = {
    // Get users
    userRepo.readAll().map { users =>
      // Sort users
      users.sortBy { user =>
        user.name
      }
    }
  }

}
