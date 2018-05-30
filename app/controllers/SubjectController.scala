package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import models._

case class SubjectData(title: String)

class SubjectController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  userRepo: UserRepository,
  subjectRepo: SubjectRepository
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) with play.api.i18n.I18nSupport {

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
              .flashing("danger" -> "Subject not found")
          )
      }
    }
  }

  def PermittedAction(implicit ec: ExecutionContext) =
    new ActionFilter[SubjectRequest] {
    def executionContext = ec
    def filter[A](input: SubjectRequest[A]) = Future {
      if (input.subject.userId != input.user._id.get) {
        Some(
          Redirect(routes.ClassController.index())
            .flashing("danger" -> "Not allowed to edit that subject")
        )
      } else {
        None
      }
    }
  }

  val subjectForm = Form(
    mapping(
      "title" -> nonEmptyText
    )(SubjectData.apply)(SubjectData.unapply)
  )

  def edit(id: BSONObjectID) =
    (userAction andThen SubjectAction(id) andThen PermittedAction) {
      implicit request =>
        request.subject.title match {
          case Some(title) =>
            // Subject title exists
            val filledForm = subjectForm.fill(
              SubjectData(request.subject.title.get)
            )
            Ok(views.html.subjects.edit(request.subject, filledForm))
          case None =>
            // Subject title does not exist
            Ok(views.html.subjects.edit(request.subject, subjectForm))
        }
  }

  def update(id: BSONObjectID) =
    (userAction andThen SubjectAction(id) andThen PermittedAction).async {
      implicit request =>
        subjectForm.bindFromRequest.fold(
          formWithErrors => {
            Future {
              BadRequest(
                views.html.subjects.edit(request.subject, formWithErrors)
              )
            }
          },
          subjectData => {
            updateSubjectTitle(id, subjectData.title).map { _ =>
              Redirect(routes.ClassController.index())
                .flashing("success" -> "Successfully edited subject")
            }
          }
        )
  }

  // Update subject title in database
  def updateSubjectTitle(id: BSONObjectID, newTitle: String): Future[Unit] = {
    subjectRepo.read(id).map { maybeSubject =>
      maybeSubject.map { subject =>
        subjectRepo.update(id, Subject(
          code = subject.code,
          semester = subject.semester,
          title = Some(newTitle),
          userId = subject.userId
        )).map { _ =>
          Logger.info(s"Updated Subject(${id}, ${newTitle})")
        }
      }
    }
  }

}
