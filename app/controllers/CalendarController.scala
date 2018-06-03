package controllers

import java.text.SimpleDateFormat
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable.ListBuffer
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import models._

class CalendarController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  classController: ClassController,
  subjectController: SubjectController
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  case class Event(
    id: String,
    title: String,
    allDay: Boolean,
    start: String,
    end: String
  )

  implicit val eventWrites: Writes[Event] = (
    (JsPath \ "id").write[String] and
    (JsPath \ "title").write[String] and
    (JsPath \ "allDay").write[Boolean] and
    (JsPath \ "start").write[String] and
    (JsPath \ "end").write[String]
  )(unlift(Event.unapply))

  def index = userAction { implicit request =>
    Ok(views.html.calendar.index())
  }

  def events(start: String, end: String) = userAction.async {
    implicit request =>
      val email = request.user.email
      getEvents(email).map { events =>
        // Filter events based on queried date range
        val startInteger = subjectController.dateInteger(start)
        val endInteger = subjectController.dateInteger(end)
        val filteredEvents = events.filter { event =>
          subjectController.dateInteger(event.start) >= startInteger &&
          subjectController.dateInteger(event.end) <= endInteger
        }
        Ok(Json.toJson(filteredEvents))
      }
  }

  // Get all classes as calendar events
  def getEvents(email: String): Future[List[Event]] = {
    // Find subjects
    var events = ListBuffer[Event]()
    classController.getClasses(email).map { maybeSubjectMap =>
      maybeSubjectMap match {
        case Some(subjectMap) =>
          // Flatten map to get class with subject and venue
          var classItems = ListBuffer[(Subject, Class, Option[String])]()
          subjectMap.foreach { item =>
            val (subject, classTuples) = item
            classTuples.foreach { tuple =>
              val (class_, venue) = tuple
              val individualClass = (subject, class_, venue)
              classItems += individualClass
            }
          }
          // Only need classes with details
          classItems = classItems.filter { classItem =>
            classItem._2.day.isDefined
          }
          classItems.toList.map { classItem =>
            val (subject, class_, venue) = classItem
            Event(
              id = class_._id.get.stringify,
              title = s"${subject.code} ${class_.category} ${class_.group}",
              allDay = false,
              start = s"2018-06-04T${timeString(class_.startTime.get)}",
              end = s"2018-06-04T${timeString(class_.endTime.get)}"
            )
          }
        case None =>
          events.toList
      }
    }
  }

  // Convert time string in database to moment time string
  def timeString(time: String): String = {
    // Convert to 24-hour format
    val inputTimeFormat = new SimpleDateFormat("hh:mma")
    val outputTimeFormat = new SimpleDateFormat("HH:mm")
    val timeObject = inputTimeFormat.parse(time)
    val convertedTime = outputTimeFormat.format(timeObject)
    convertedTime + ":00"
  }

}
