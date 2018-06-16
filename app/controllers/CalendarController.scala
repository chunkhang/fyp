package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import reactivemongo.bson.BSONObjectID
import models._
import helpers.Utils

class CalendarController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  classController: ClassController,
  userRepo: UserRepository,
  subjectRepo: SubjectRepository,
  classRepo: ClassRepository,
  taskRepo: TaskRepository,
  utils: Utils
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  case class EventData(
    subjectItem: Subject,
    classItem: Class,
    venue: String,
    start: String,
    end: String,
    replacement: Boolean = false
  )

  case class JsonEvent(
    title: String,
    start: String,
    end: String,
    modalSubjectCode: String,
    modalSubjectName: String,
    modalClass: String,
    modalDate: String,
    modalTime: String,
    modalVenue: String,
    modalClassId: String,
    modalDatabaseDate: String,
    modalReplacement: Boolean
  )
  implicit val eventWriter = Json.writes[JsonEvent]

  case class JsonTask(
    title: String,
    start: String,
    modalEnd: String,
    modalTask: String,
    modalSubject: String,
    modalTitle: String,
    modalScore: Int,
    modalDate: String,
    modalDescription: String
  )
  implicit val taskWriter = Json.writes[JsonTask]

  case class JsonWorkload(
    taskTitle: String,
    taskScore: Int,
    taskDate: String,
    subjectTitle: String,
    lecturerName: String,
    students: Int
  )
  implicit val workloadWriter = Json.writes[JsonWorkload]

  def index = userAction.async { implicit request =>
    subjectRepo.findSubjectsByUserId(request.user._id.get).map { allSubjects =>
      val subjects = allSubjects.filter { subject =>
        // Only need subjects that have details
        subject.title.isDefined
      } sortWith (
        // Sort ascendingly by subject code
        _.code < _.code
      )
      if (!subjects.isEmpty) {
        Ok(views.html.calendar.index(Some(subjects)))
      } else {
        Ok(views.html.calendar.index(None))
      }
    }
  }

  def events(view: String, start: String, end: String) = userAction.async {
    implicit request =>
      val email = request.user.email
      getEventData(email).map { maybeEventData =>
        var events = List[JsonEvent]()
        maybeEventData match {
          case Some(eventData) =>
            var eventTitles = List[String]()
            view match {
              case "month" =>
                eventTitles = eventData.map { data =>
                  s"${data.subjectItem.code} " +
                  s"${data.classItem.category(0)}${data.classItem.group}"
                }
              case "week" =>
                eventTitles = eventData.map { data =>
                  s"${data.subjectItem.code}\n" +
                  s"${data.classItem.category} " +
                  s"Group ${data.classItem.group}"
                }
              case "list" =>
                eventTitles = eventData.map { data =>
                  s"${data.subjectItem.code} | " +
                  s"${data.subjectItem.title.get} | " +
                  s"${data.classItem.category} " +
                  s"Group ${data.classItem.group}"
                }
              case _ =>
            }
            events = (eventData zip eventTitles).map { tuple =>
              var (data, eventTitle) = tuple
              var modalTime_ = ""
              if (!data.replacement) {
                modalTime_ =
                  s"${data.classItem.startTime.get} - " +
                  s"${data.classItem.endTime.get}",
              } else {
                modalTime_ =
                  s"${utils.unmomentTime(data.start)} - " +
                  s"${utils.unmomentTime(data.end)}",
              }
              JsonEvent(
                title = eventTitle,
                start = utils.appendTimezone(data.start),
                end = utils.appendTimezone(data.end),
                modalSubjectCode = data.subjectItem.code,
                modalSubjectName = data.subjectItem.title.get,
                modalClass =
                  s"${data.classItem.category} " +
                  s"Group ${data.classItem.group}",
                modalDate = utils.eventModalDate(data.start),
                modalTime = modalTime_,
                modalVenue = data.venue,
                modalClassId = data.classItem._id.get.stringify,
                modalDatabaseDate = data.start,
                modalReplacement = data.replacement
              )
            }
            // Filter events based on queried date range
            val startInteger = utils.totalDays(start)
            val endInteger = utils.totalDays(end)
            events = events.filter { event =>
              utils.totalDays(event.start) >= startInteger &&
              utils.totalDays(event.end) <= endInteger
            }
            Ok(Json.toJson(events))
          case None =>
            Ok(Json.toJson(events))
        }
      }
  }

  def tasks(view: String, start: String, end: String) = userAction.async {
    implicit request =>
      var events = ListBuffer[JsonTask]()
      // Find subjects under lecturer
      subjectRepo.findSubjectsByUserId(request.user._id.get).flatMap {
        subjects =>
          // Find tasks under those subjects
          val subjectIds = subjects.filter { subject =>
            // Only subjects with details
            subject.title.isDefined
          } map(_._id.get)
          taskRepo.readAll().map { allTasks =>
            val tasks = allTasks.filter { task =>
              subjectIds.contains(task.subjectId)
            }
            tasks.foreach { task =>
              val getSubject = subjectRepo.read(task.subjectId).map {
                maybeSubject => maybeSubject.get
              }
              val subject = Await.result(getSubject, 5.seconds)
              var taskTitle = ""
              view match {
                case "month" =>
                  taskTitle = s"${subject.code} ${task.title}"
                case "week" =>
                  taskTitle = s"${subject.code} ${task.title}"
                case "list" =>
                  taskTitle =
                    s"${subject.code} | ${subject.title.get} | ${task.title}"
                case _ =>
              }
              // Create events
              events += JsonTask(
                title = taskTitle,
                start = task.dueDate,
                modalEnd = utils.appendTimezone(
                  utils.momentTime(task.dueDate, "06:00PM")
                ),
                modalTask = task._id.get.stringify,
                modalSubject = subject.code,
                modalTitle = task.title,
                modalScore = task.score,
                modalDate = task.dueDate,
                modalDescription = task.description
              )
            }
            Ok(Json.toJson(events))
          }
      }
  }

  def workload = userAction.async { implicit request =>
    // Get all subjects under other lecturers
    subjectRepo.readAll().flatMap { allSubjects =>
      val subjects = allSubjects.filter { subject =>
        subject.userId != request.user._id.get &&
        subject.title.isDefined
      }
      val subjectIds = subjects.map(_._id.get)
      // Get all tasks under them
      taskRepo.readAll().flatMap { allTasks =>
        val tasks = allTasks.filter { task =>
          subjectIds.contains(task.subjectId)
        }
        val taskTuples: List[(Task, Subject, User)]  = tasks.map { task =>
          val taskSubject = allSubjects.filter { subject =>
            task.subjectId == subject._id.get
          } (0)
          val getLecturer = userRepo.read(taskSubject.userId).map {
            maybeUser =>
              maybeUser.get
          }
          val taskLecturer = Await.result(getLecturer, 5.seconds)
          (task, taskSubject, taskLecturer)
        }
        // Get all the lecturer's classes
        val mySubjects = allSubjects.filter { subject =>
          subject.userId == request.user._id.get &&
          subject.title.isDefined
        }
        val mySubjectIds = mySubjects.map(_._id.get)
        Future.traverse(mySubjectIds) { subjectId =>
          classRepo.findClassesBySubjectId(subjectId).map { classes =>
            classes
          }
        }.flatMap { classLists =>
          val myClasses = classLists.flatten
          // Get all the lecturer's students
          val myStudents = utils.subjectStudents(myClasses)
          // How many lecturer's students are involved with each task
          Future.traverse(taskTuples) { tuple =>
            val (task, subject, lecturer) = tuple
            classRepo.findClassesBySubjectId(subject._id.get).map { classes =>
              val subjectStudents = utils.subjectStudents(classes)
              val taskStudents = myStudents intersect subjectStudents
              taskStudents
            }
          }.map { taskStudentsList =>
            val workload = (taskTuples zip taskStudentsList).map  { tuple =>
              val ((task, subject, lecturer), taskStudents) = tuple
              JsonWorkload(
                taskTitle = task.title,
                taskScore = task.score,
                taskDate = task.dueDate,
                subjectTitle = subject.title.get,
                lecturerName = lecturer.name,
                students = taskStudents.length
              )
            }
            Ok(Json.obj(
              "status" -> "success",
              "totalStudents" -> myStudents.length,
              "workload" -> workload
            ))
          }
        }
      }

    }
  }

  // Get add data required to create calendar event for classes
  def getEventData(email: String):
    Future[Option[List[EventData]]] = {
    // Find subjects
    classController.getClasses(email).map { maybeSubjectMap =>
      maybeSubjectMap match {
        case Some(subjectMap) =>
          // Flatten map to get class with subject and venue
          var classes = ListBuffer[(Subject, Class, Option[String])]()
          subjectMap.foreach { item =>
            val (subject, classTuples) = item
            classTuples.foreach { tuple =>
              val (class_, venue) = tuple
              val individualClass = (subject, class_, venue)
              classes += individualClass
            }
          }
          // Only need classes with details
          val detailedClasses: List[(Subject, Class, String)] =
            classes.toList.filter { classItem =>
              classItem._2.day.isDefined
            } map { classItem =>
              (classItem._1, classItem._2, classItem._3.get)
            }
          // Convert classes to event data
          var eventData = ListBuffer[EventData]()
          detailedClasses.map { tuple =>
            var (subject, class_, venue_) = tuple
            // First day of class from start date
            val firstDate = utils.firstDate(subject.semester, class_.day.get)
            // Repeat classes weekly until end date
            val dates = utils.weeklyDates(firstDate, subject.endDate.get)
            // Do not include cancelled classes
            dates.filter { date =>
              val cancelledDates =
                class_.exceptionDates.getOrElse(List[String]())
              !cancelledDates.contains(date)
            } foreach { date =>
              eventData += EventData(
                subjectItem = subject,
                classItem = class_,
                venue = venue_,
                start = utils.momentTime(date, class_.startTime.get),
                end = utils.momentTime(date, class_.endTime.get),
              )
            }
            // Replacement classes
            if (class_.replacements.isDefined) {
              class_.replacements.get.foreach { replacement =>
                val getVenue =
                  classController.getVenueName(replacement.venueId).map {
                    venue => venue
                  }
                eventData += EventData(
                  subjectItem = subject,
                  classItem = class_,
                  venue = Await.result(getVenue, 5.seconds),
                  start = utils.momentTime(
                    replacement.replaceDate,
                    replacement.startTime
                  ),
                  end = utils.momentTime(
                    replacement.replaceDate,
                    replacement.endTime
                  ),
                  replacement = true
                )
              }
            }
          }
          Some(eventData.toList)
        case None =>
          None
      }
    }
  }

}
