package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import actions._
import models._

class SubjectController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  subjectRepo: SubjectRepository
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def edit(id: BSONObjectID) = userAction.async { implicit request =>
    subjectRepo.read(id).map { maybeSubject =>
      maybeSubject match {
        case Some(subject) =>
          Ok("!")
        case None =>
          Redirect(routes.ClassController.index())
      }
    }
  }

  def update(id: BSONObjectID) = userAction { implicit request =>
    Ok("!")
  }

}
