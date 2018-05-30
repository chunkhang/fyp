package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.async.Async.{async, await}
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import models._

class SubjectController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  userRepo: UserRepository,
  subjectRepo: SubjectRepository
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  class SubjectRequest[A](
    val subject: Subject,
    request: UserRequest[A]
  ) extends WrappedRequest[A](request) {
    def user = request.user
  }

  def SubjectAction(id: BSONObjectID)(implicit ec: ExecutionContext) =
    new ActionRefiner[UserRequest, SubjectRequest] {
    def executionContext = ec
    def refine[A](input: UserRequest[A]) =  {
      subjectRepo.read(id).map { maybeSubject =>
        maybeSubject
          .map(subject => new SubjectRequest(subject, input))
          .toRight(
            Redirect(routes.ClassController.index())
              .flashing("message" -> "Subject not found")
          )
      }
    }
  }

  def PermissionCheckAction(implicit ec: ExecutionContext) =
    new ActionFilter[SubjectRequest] {
    def executionContext = ec
    def filter[A](input: SubjectRequest[A]) = Future {
      if (input.subject.userId != input.user._id.get) {
        Some(
          Redirect(routes.ClassController.index())
            .flashing("message" -> "Not allowed to view that subject")
        )
      } else {
        None
      }
    }
  }

  def edit(id: BSONObjectID) =
    (userAction andThen SubjectAction(id) andThen PermissionCheckAction) {
      implicit request =>
        Ok(views.html.subjects.edit(request.subject))
  }

  def update(id: BSONObjectID) =
    (userAction andThen SubjectAction(id) andThen PermissionCheckAction) {
      implicit request =>
        Ok("!")
  }

}
