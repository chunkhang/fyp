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

  case class Event(
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

  implicit val eventWrites: Writes[Event] = (
    (JsPath \ "title").write[String] and
    (JsPath \ "start").write[String] and
    (JsPath \ "end").write[String] and
    (JsPath \ "modalSubjectCode").write[String] and
    (JsPath \ "modalSubjectName").write[String] and
    (JsPath \ "modalClass").write[String] and
    (JsPath \ "modalDate").write[String] and
    (JsPath \ "modalTime").write[String] and
    (JsPath \ "modalVenue").write[String] and
    (JsPath \ "modalClassId").write[String] and
    (JsPath \ "modalDatabaseDate").write[String] and
    (JsPath \ "modalReplacement").write[Boolean]
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
            var eventTitles = List[String]()
            view match {
              case "month" =>
                eventTitles = eventData.map { data =>
                  var title =
                    s"${data.subjectItem.code} " +
                    s"${data.classItem.category(0)}${data.classItem.group}"
                  if (data.replacement) {
                    title += " [ R ]"
                  }
                  title
                }
              case "week" =>
                eventTitles = eventData.map { data =>
                  var title =
                    s"${data.subjectItem.code}\n" +
                    s"${data.classItem.category} " +
                    s"Group ${data.classItem.group}"
                  if (data.replacement) {
                    title += "\n[ Replacement ]"
                  }
                  title
                }
              case "list" =>
                eventTitles = eventData.map { data =>
                  var title =
                    s"${data.subjectItem.code} | " +
                    s"${data.subjectItem.title.get} | " +
                    s"${data.classItem.category} " +
                    s"Group ${data.classItem.group}"
                  if (data.replacement) {
                    title += " | Replacement"
                  }
                  title
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
              Event(
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
