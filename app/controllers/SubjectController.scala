package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.Logger
import play.api.libs.json.Json
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
  taskRepo: TaskRepository,
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

  def createTask(id: BSONObjectID) =
    (userAction andThen SubjectAction(id) andThen PermittedAction).async {
      implicit request =>
        request.body.asJson.map { json =>
          (json \ "title").asOpt[String].map { title_ =>
            (json \ "score").asOpt[Int].map { score_ =>
              (json \ "dueDate").asOpt[String].map { dueDate_ =>
                (json \ "description").asOpt[String].map { description_ =>
                  // Create task in database
                  val taskId = BSONObjectID.generate
                  val task = Task(
                    _id = Option(taskId),
                    title = title_,
                    score = score_,
                    dueDate = dueDate_,
                    description = description_.trim,
                    uid = Uid.random().getValue(),
                    sequence = 0,
                    subjectId = id
                  )
                  taskRepo.create(task).flatMap { _ =>
                    Logger.info(s"Created Task(${taskId})")
                    // Create ical
                    val biweeklyTaskIcal = utils.biweeklyTaskIcal(
                      task.uid,
                      task.sequence,
                      task,
                      request.subjectItem,
                      request.user
                    )
                    // Find all students for subject
                    getAllStudents(id).map { students =>
                      // Send ical
                      mailer.sendIcs(
                        subject =
                          s"Added Task: ${request.subjectItem.title.get} " +
                          s"${task.title}",
                          toList = utils.studentEmails(students),
                          ics = biweeklyTaskIcal,
                          isTask = true
                      )
                      Ok(Json.obj("status" -> "success"))
                    }
                  }
                } getOrElse {
                  Future {
                    BadRequest("Missing parameter \"description\"")
                  }
                }
              } getOrElse {
                Future {
                  BadRequest("Missing parameter \"dueDate\"")
                }
              }
            } getOrElse {
              Future {
                BadRequest("Missing parameter \"score\"")
              }
            }
          } getOrElse {
            Future {
              BadRequest("Missing parameter \"title\"")
            }
          }
        } getOrElse {
          Future {
            BadRequest("Expecting json data")
          }
        }
  }

  def updateTask(id: BSONObjectID) =
    (userAction andThen SubjectAction(id) andThen PermittedAction).async {
      implicit request =>
        request.body.asJson.map { json =>
          (json \ "taskId").asOpt[String].map { maybeTaskId =>
            (json \ "title").asOpt[String].map { title_ =>
              (json \ "score").asOpt[Int].map { score_ =>
                (json \ "dueDate").asOpt[String].map { dueDate_ =>
                  (json \ "description").asOpt[String].map { description_ =>
                    // Update task in database
                    val taskId = BSONObjectID.parse(maybeTaskId).get
                    taskRepo.read(taskId).flatMap { maybeTask =>
                      val task = maybeTask.get
                      val newTask = task.copy(
                        title = title_,
                        score = score_,
                        dueDate = dueDate_,
                        description = description_.trim,
                        sequence = task.sequence + 1,
                        subjectId = id
                      )
                      taskRepo.update(taskId, newTask).flatMap { _ =>
                        Logger.info(s"Updated Task(${taskId})")
                        // Create ical
                        val biweeklyTaskIcal = utils.biweeklyTaskIcal(
                          newTask.uid,
                          newTask.sequence,
                          newTask,
                          request.subjectItem,
                          request.user
                        )
                        // Find all students for subject
                        getAllStudents(id).map { students =>
                          // Send ical
                          val subjectTitle =
                            request.subjectItem.title.get
                          mailer.sendIcs(
                            subject =
                              s"Updated Task: ${subjectTitle} "+
                              s"${task.title}",
                              toList = utils.studentEmails(students),
                              ics = biweeklyTaskIcal,
                              isTask = true
                          )
                          Ok(Json.obj("status" -> "success"))
                        }
                      }
                    }
                  } getOrElse {
                    Future {
                      BadRequest("Missing parameter \"description\"")
                    }
                  }
                } getOrElse {
                  Future {
                    BadRequest("Missing parameter \"dueDate\"")
                  }
                }
              } getOrElse {
                Future {
                  BadRequest("Missing parameter \"score\"")
                }
              }
            } getOrElse {
              Future {
                BadRequest("Missing parameter \"title\"")
              }
            }
          } getOrElse {
            Future {
              BadRequest("Missing parameter \"taskId\"")
            }
          }
        } getOrElse {
          Future {
            BadRequest("Expecting json data")
          }
        }
  }

  def deleteTask(id: BSONObjectID) =
    (userAction andThen SubjectAction(id) andThen PermittedAction).async {
      implicit request =>
        request.body.asJson.map { json =>
          (json \ "taskId").asOpt[String].map { maybeTaskId =>
            // Delete task from database
            val taskId = BSONObjectID.parse(maybeTaskId).get
            taskRepo.delete(taskId).flatMap { maybeTask =>
              Logger.info(s"Deleted Task(${taskId})")
              val task = maybeTask.get
              // Update ical
              val biweeklyTaskIcal = utils.biweeklyTaskIcal(
                task.uid,
                task.sequence + 1,
                task,
                request.subjectItem,
                request.user,
                delete = true
              )
              // Find all students for subject
              getAllStudents(id).map { students =>
                // Send ical
                mailer.sendIcs(
                  subject =
                    s"Deleted Task: ${request.subjectItem.title.get} " +
                    s"${task.title}",
                    toList = utils.studentEmails(students),
                    ics = biweeklyTaskIcal,
                    isTask = true,
                    delete = true
                )
              Ok(Json.obj("status" -> "success"))
              }
            }
          } getOrElse {
            Future {
              BadRequest("Missing parameter \"taskId\"")
            }
          }
        } getOrElse {
          Future {
            BadRequest("Expecting json data")
          }
        }
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

  // Get all students under subject
  def getAllStudents(id: BSONObjectID): Future[List[String]] = {
    getSubjectWithClasses(id).map { tuple =>
      val (subject, classes) = tuple
      var studentLists = ListBuffer[List[String]]()
      classes.foreach { class_ =>
        studentLists += class_.students
      }
      val students = studentLists.toList.distinct.fold(List[String]())(_ ++ _)
      students
    }
  }

}
