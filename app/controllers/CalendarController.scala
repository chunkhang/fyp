package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable.ListBuffer
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import models._
import helpers.Utils

class CalendarController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  classController: ClassController,
  utils: Utils
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  case class Event(
    title: String,
    allDay: Boolean,
    start: String,
    end: String,
    venue: String,
    startEditable: Boolean
  )

  implicit val eventWrites: Writes[Event] = (
    (JsPath \ "title").write[String] and
    (JsPath \ "allDay").write[Boolean] and
    (JsPath \ "start").write[String] and
    (JsPath \ "end").write[String] and
    (JsPath \ "venue").write[String] and
    (JsPath \ "startEditable").write[Boolean]
  )(unlift(Event.unapply))

  def index = userAction { implicit request =>
    Ok(views.html.calendar.index())
  }

  def events(start: String, end: String) = userAction.async {
    implicit request =>
      val email = request.user.email
      getEvents(email).map { events =>
        // Filter events based on queried date range
        val startInteger = utils.totalDays(start)
        val endInteger = utils.totalDays(end)
        val filteredEvents = events.filter { event =>
          utils.totalDays(event.start) >= startInteger &&
          utils.totalDays(event.end) <= endInteger
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
          // Create events
          var events = ListBuffer[Event]()
          classItems.toList.map { classItem =>
            val (subject, class_, venue) = classItem
            // First day of class from start date
            val firstDate = utils.firstDate(subject.semester, class_.day.get)
            // Repeat classes weekly until end date
            val dates =
              utils.weeklyDates(firstDate, subject.endDate.get)
            dates.foreach { date =>
              events += Event(
                title = s"${subject.code} ${class_.category} ${class_.group}",
                allDay = false,
                start = utils.momentTime(date, class_.startTime.get),
                end = utils.momentTime(date, class_.endTime.get),
                venue = venue.get,
                startEditable = true
              )
            }
          }
          events.toList
        case None =>
          events.toList
      }
    }
  }

}
