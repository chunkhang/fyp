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

  case class EventData(
    subjectItem: Subject,
    classItem: Class,
    venue: String,
    start: String,
    end: String
  )

  case class Event(
    title: String,
    start: String,
    end: String
  )

  implicit val eventWrites: Writes[Event] = (
    (JsPath \ "title").write[String] and
    (JsPath \ "start").write[String] and
    (JsPath \ "end").write[String]
  )(unlift(Event.unapply))

  def index = userAction { implicit request =>
    Ok(views.html.calendar.index())
  }

  def events(view: String, start: String, end: String) = userAction.async {
    implicit request =>
      val email = request.user.email
      getEventData(email).map { maybeEventData =>
        var events = List[Event]()
        maybeEventData match {
          case Some(eventData) =>
            view match {
              case "month" =>
                events = eventData.map { data =>
                  Event(
                    title =
                      s"${data.subjectItem.code} " +
                      s"${data.classItem.category(0)}${data.classItem.group}",
                    start = data.start,
                    end = data.end
                  )
                }
              case "week" =>
                events = eventData.map { data =>
                  Event(
                    title = "Week",
                    start = data.start,
                    end = data.end
                  )
                }
              case "list" =>
                events = eventData.map { data =>
                  Event(
                    title = "List",
                    start = data.start,
                    end = data.end
                  )
                }
              case _ =>
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
            dates.foreach { date =>
              eventData += EventData(
                subjectItem = subject,
                classItem = class_,
                venue = venue_,
                start = utils.momentTime(date, class_.startTime.get),
                end = utils.momentTime(date, class_.endTime.get),
              )
            }
          }
          Some(eventData.toList)
        case None =>
          None
      }
    }
  }

}
