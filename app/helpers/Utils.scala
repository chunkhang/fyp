package helpers

import javax.inject.Inject
import java.util.Date
import java.text.SimpleDateFormat
import scala.collection.mutable.ListBuffer
import play.api.Configuration
import com.github.nscala_time.time.Imports._
import biweekly._
import biweekly.component._
import biweekly.property._
import biweekly.util._
import models._

case class Ical(
  summary: String,
  date: String,
  startTime: String,
  endTime: String,
  location: String,
  description: String,
  exceptionDates: Option[List[String]]
)

class Utils @Inject()(config: Configuration) {

  // Append url with query params
  def urlWithParams(url: String, params: Map[String, String]) = {
    url + "?" + params.foldLeft("")( (acc, kv) =>
      acc + "&" + kv._1 + "=" + kv._2
    ).substring(1)
  }

  // Convert from 12-hour to 24-hour format
  def twentyFourHour(time: String): String = {
    val inputFormatter = DateTimeFormat.forPattern("hh:mmaa");
    val outputFormatter = DateTimeFormat.forPattern("HH:mm")
    val dateTime = inputFormatter.parseDateTime(time)
    dateTime.toString(outputFormatter)
  }

  // Convert to moment time string
  def momentTime(date: String, time: String): String = {
     date + "T" + twentyFourHour(time) + ":00"
  }

  // Convert time string to integer for comparison
  def totalMinutes(time: String): Int = {
    // Convert to total minutes
    val timeString = twentyFourHour(time)
    val hours = timeString.slice(0, 2).toInt
    val minutes = timeString.slice(3, 5).toInt
    (hours * 60) + minutes
  }

  // Convert date string to integer for comparison
  def totalDays(date: String): Int = {
    // Convert to total days
    val years = date.slice(0, 4).toInt
    val months = date.slice(5, 7).toInt
    val days = date.slice(8, 10).toInt
    (years * 365) + (months * 30) + days
  }

  // Get first date given start date and recurring day
  def firstDate(startDate: String, day: String): String = {
    // Convert to day of week integer
    val inputFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    val outputFormatter = DateTimeFormat.forPattern("e")
    val startDateTime = inputFormatter.parseDateTime(startDate)
    val startDay = startDateTime.toString(outputFormatter).toInt
    val recurringDay = DateTimeFormat.forPattern("EEEE").parseDateTime(day)
      .toString(outputFormatter).toInt
    // Calculate days to add to start date
    var daysToAdd = 0
    if (recurringDay > startDay) {
      daysToAdd = recurringDay - startDay
    } else if (recurringDay < startDay) {
      daysToAdd = 7 - (startDay - recurringDay)
    }
    val firstDateTime = startDateTime + daysToAdd.days
    firstDateTime.toString(inputFormatter)
  }

  // Get all weekly recurring dates given first date and end date
  def weeklyDates(firstDate: String, endDate: String): List[String] = {
    val inputFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    val firstDateTime = inputFormatter.parseDateTime(firstDate)
    val endDateTime = inputFormatter.parseDateTime(endDate)
    var dates = ListBuffer[DateTime]()
    var currentDateTime = firstDateTime
    while (currentDateTime <= endDateTime) {
      dates += currentDateTime
      currentDateTime += 7.days
    }
    dates.toList.map { date =>
      date.toString(inputFormatter)
    }
  }

  // Generate ical object for class containing event details
  def classIcal(
    user: User,
    subjectItem: Subject,
    classItem: Class,
    venue: String
  ): Ical = {
    Ical(
      summary = s"${subjectItem.title.get} (${classItem.category})",
      date = firstDate(subjectItem.semester, classItem.day.get),
      startTime = classItem.startTime.get,
      endTime = classItem.endTime.get,
      location = venue,
      description = s"""
        |${subjectItem.title.get} (${subjectItem.code})
        |${classItem.category} (Group ${classItem.group})
        |
        |Every ${classItem.day.get}
        |${classItem.startTime.get} - ${classItem.endTime.get}
        |${venue}
        |
        |${user.name} (${user.email})
      """.stripMargin.trim,
      exceptionDates = classItem.exceptionDates
    )
  }

  // Create biweekly ical event from ical object
  def biweeklyIcal(
    method: String,
    uid: Uid,
    sequence: Int,
    ical: Ical,
    recurUntil: Option[String] = None
  ): ICalendar = {
    val icalendar = new ICalendar()
    icalendar.setMethod(method)
    val event = new VEvent()
    event.setUid(uid)
    event.setOrganizer(config.get[String]("play.mailer.user"))
    event.setSequence(sequence)
    event.setSummary(ical.summary)
    val formatter = new SimpleDateFormat("yyyy-MM-dd hh:mmaa")
    val dateStart = new DateStart(
      formatter.parse(s"${ical.date} ${ical.startTime}")
    )
    val dateEnd = new DateEnd(formatter.parse(s"${ical.date} ${ical.endTime}"))
    event.setDateStart(dateStart)
    event.setDateEnd(dateEnd)
    event.setLocation(ical.location)
    event.setDescription(ical.description)
    if (recurUntil.isDefined) {
      val jodaFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
      val recurEndDate =
        (jodaFormatter.parseDateTime(recurUntil.get) + 1.day)
        .toString(jodaFormatter)
      val simpleFormatter = new SimpleDateFormat("yyyy-MM-dd")
      val recurDateEnd = simpleFormatter.parse(recurEndDate)
      val recurrenceRule =
        new Recurrence.Builder(Frequency.WEEKLY).until(recurDateEnd, false)
        .build()
      event.setRecurrenceRule(recurrenceRule)
      if (ical.exceptionDates.isDefined) {
        val exceptionDates = new ExceptionDates()
        ical.exceptionDates.get.foreach { exceptionDate =>
          exceptionDates.getValues().add(
            new ICalDate(simpleFormatter.parse(exceptionDate), false)
          )
        }
        event.addExceptionDates(exceptionDates)
      }
    }
    icalendar.addEvent(event)
    icalendar
  }

  // Convert student list to list of student emails
  def studentEmails(students: List[String]): List[String] = {
    students.map { student =>
      student + "@" + config.get[String]("my.domain.student")
    }
  }

  // Return current timestamp in milliseconds
  def timestampNow(): String = {
    System.currentTimeMillis().toString
  }

  // Format date for event modal
  def eventModalDate(dateTime: String): String = {
    val date = dateTime.split("T")(0)
    val jodaDate = DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(date)
    jodaDate.toString(DateTimeFormat.forPattern("MMM d, yyyy (E)"))
  }

  // Format date for database
  def databaseDate(date: String): String = {
    val jodaDate =
      DateTimeFormat.forPattern("MMM d, yyyy (E)").parseDateTime(date)
    jodaDate.toString(DateTimeFormat.forPattern("yyyy-MM-dd"))
  }

}
