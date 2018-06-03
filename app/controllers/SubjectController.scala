package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import models._

case class SubjectData(
  title: String,
  endDate: String
)

class SubjectController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  userRepo: UserRepository,
  subjectRepo: SubjectRepository
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) with play.api.i18n.I18nSupport {

  class SubjectRequest[A](
    val subjectItem: Subject,
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
      if (input.subjectItem.userId != input.user._id.get) {
        Some(
          Redirect(routes.ClassController.index())
            .flashing("danger" -> "Not your subject")
        )
      } else {
        None
      }
    }
  }

  // Convert date string to integer for comparison
  def dateInteger(date: String): Int = {
    // Convert to total days
    val years = date.slice(0, 4).toInt
    val months = date.slice(5, 7).toInt
    val days = date.slice(8, 10).toInt
    (years * 365) + (months * 30) + days
  }

  def validateDate(endDate: String) = {
    val result = subjectRepo.readAll().map { subjects =>
      val start = dateInteger(subjects(0).semester)
      val end = dateInteger(endDate)
      if (end > start) {
        Some(endDate)
      } else {
        None
      }
    }
    Await.result(result, 5.seconds)
  }

  def validatePeriod(endDate: String) = {
    val result = subjectRepo.readAll().map { subjects =>
      val start = dateInteger(subjects(0).semester)
      val end = dateInteger(endDate)
      val period = end - start
      if (period >= 60 && period <= 180) {
        Some(endDate)
      } else {
        None
      }
    }
    Await.result(result, 5.seconds)
  }

  val subjectForm = Form(
    mapping(
      "Title" -> nonEmptyText(maxLength = 40),
      "End Date" -> nonEmptyText
    )(SubjectData.apply)(SubjectData.unapply)
    verifying(
      "End date must be later than start date",
      fields => fields match {
        case subjectData =>
          validateDate(subjectData.endDate).isDefined
      }
    )
    verifying(
      "Subject period: 60 - 180 days",
      fields => fields match {
        case subjectData =>
          validatePeriod(subjectData.endDate).isDefined
      }
    )
  )

  def edit(id: BSONObjectID) =
    (userAction andThen SubjectAction(id) andThen PermittedAction) {
      implicit request =>
        request.subjectItem.title match {
          case Some(title) =>
            // Subject title exists
            val filledForm = subjectForm.fill(
              SubjectData(
                request.subjectItem.title.get,
                request.subjectItem.endDate.get
              )
            )
            Ok(views.html.subjects.edit(request.subjectItem, filledForm))
          case None =>
            // Subject title does not exist
            Ok(views.html.subjects.edit(request.subjectItem, subjectForm))
        }
  }

  def update(id: BSONObjectID) =
    (userAction andThen SubjectAction(id) andThen PermittedAction).async {
      implicit request =>
        subjectForm.bindFromRequest.fold(
          formWithErrors => {
            Future {
              BadRequest(
                views.html.subjects.edit(request.subjectItem, formWithErrors)
              )
            }
          },
          subjectData => {
            subjectRepo.read(id).flatMap { maybeSubject =>
              val subject = maybeSubject.get
                subjectRepo.update(id, subject.copy(
                  title = Some(subjectData.title),
                  endDate = Some(subjectData.endDate)
                )).map { _ =>
                  Logger.info(s"Updated Subject(${id})")
                  Redirect(routes.ClassController.index())
                    .flashing("success" -> "Successfully edited subject")
                }
            }
          }
        )
  }

}
