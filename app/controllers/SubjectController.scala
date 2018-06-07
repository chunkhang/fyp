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
import biweekly.property._
import models._
import helpers.Utils
import mailer.Mailer

case class SubjectData(
  title: String,
  endDate: String
)

class SubjectController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  userRepo: UserRepository,
  subjectRepo: SubjectRepository,
  classRepo: ClassRepository,
  classController: ClassController,
  utils: Utils,
  mailer: Mailer
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

  def validateDate(endDate: String) = {
    val result = subjectRepo.readAll().map { subjects =>
      val start = utils.totalDays(subjects(0).semester)
      val end = utils.totalDays(endDate)
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
      val start = utils.totalDays(subjects(0).semester)
      val end = utils.totalDays(endDate)
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
            // Update subject in database
            subjectRepo.read(id).flatMap { maybeSubject =>
              val subject = maybeSubject.get
                subjectRepo.update(id, subject.copy(
                  title = Some(subjectData.title),
                  endDate = Some(subjectData.endDate)
                )).map { _ =>
                  Logger.info(s"Updated Subject(${id})")
                  // Create ical if there are classes with details
                  getSubjectWithClasses(id).map { tuple =>
                    val (subject, classes) = tuple
                    val detailedClasses = classes.filter { class_ =>
                      class_.day.isDefined
                    }
                    Future.traverse(detailedClasses) { class_ =>
                      classController.getVenueName(class_.venueId.get).map {
                        venue =>
                          val classIcal = utils.classIcal(
                            request.user,
                            subject,
                            class_,
                            venue
                          )
                          // Update ical
                          val sequence_ = class_.sequence.get + 1
                          val biweeklyIcal = utils.biweeklyIcal(
                            method= "Request",
                            uid = new Uid(class_.uid.get),
                            sequence = sequence_,
                            ical = classIcal,
                            recurUntil = Some(subject.endDate.get)
                          )
                          // Send ical
                          mailer.sendIcs(
                            subject =
                              s"Updated: ${subject.title.get} " +
                              s"(${class_.category})",
                            toList = utils.studentEmails(class_.students),
                            ics = biweeklyIcal
                          )
                          // Update ical sequence in database
                          classRepo.update(class_._id.get, class_.copy(
                            sequence = Some(sequence_)
                          )).map { _ =>
                            Logger.info(
                              s"Updated Class(${class_._id.get}, " +
                              s"sequence = ${sequence_})"
                            )
                          }
                      }
                    }
                  }
                  Redirect(routes.ClassController.index())
                    .flashing("success" -> "Successfully edited subject")
                }
            }
          }
        )
  }

  // Get subject and its classes given subject it
  def getSubjectWithClasses(subjectId: BSONObjectID):
    Future[(Subject, List[Class])] = {
    subjectRepo.read(subjectId).flatMap { maybeSubject =>
      val subject = maybeSubject.get
      classRepo.findClassesBySubjectId(subjectId).map { classes =>
        (subject, classes)
      }
    }
  }

}
