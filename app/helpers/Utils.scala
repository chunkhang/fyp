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
    val inputFormatter = DateTimeFormat.forPattern("hh:mmaa")
    val outputFormatter = DateTimeFormat.forPattern("HH:mm")
    val dateTime = inputFormatter.parseDateTime(time)
    dateTime.toString(outputFormatter)
  }

  // Convert from 24-hour to 12-hour format
  def twelveHour(time: String): String = {
    val inputFormatter = DateTimeFormat.forPattern("HH:mm")
    val outputFormatter = DateTimeFormat.forPattern("hh:mmaa")
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

  // Generate ical object for replacement class containing event details
  def replacementIcal(
    user: User,
    subjectItem: Subject,
    classItem: Class,
    originalDate: String,
    replacementDate: String,
    replacementStartTime: String,
    replacementEndTime: String,
    originalVenue: String,
    replacementVenue: String
  ): Ical = {
    Ical(
      summary =
        s"${subjectItem.title.get} (${classItem.category}) [Replacement]",
      date = replacementDate,
      startTime = replacementStartTime,
      endTime = replacementEndTime,
      location = replacementVenue,
      description = s"""
        |Replacement Details:
        |
        |${eventModalDate(replacementDate)}
        |${replacementStartTime} - ${replacementEndTime}
        |${replacementVenue}
        |
        |Original Details:
        |
        |${subjectItem.title.get} (${subjectItem.code})
        |${classItem.category} (Group ${classItem.group})
        |
        |${eventModalDate(originalDate)}
        |${classItem.startTime.get} - ${classItem.endTime.get}
        |${originalVenue}
        |
        |${user.name} (${user.email})
      """.stripMargin.trim,
      exceptionDates = classItem.exceptionDates
    )
  }

  // Create biweekly ical event from ical object
  def biweeklyIcal(
    uid: Uid,
    sequence: Int,
    ical: Ical,
    recurUntil: Option[String] = None
  ): ICalendar = {
    val icalendar = new ICalendar()
    icalendar.setMethod("PUBLISH")
    icalendar.addEvent(biweeklyEvent(
      Some(uid),
      sequence,
      ical,
      recurUntil
    ))
    icalendar
  }

  // Create biweekly ical for replacement
  def biweeklyReplacementIcal(
    uid: Uid,
    sequence: Int,
    cancelIcal: Ical,
    replaceIcal: Ical,
    recurUntil: Option[String] = None
  ): ICalendar = {
    val icalendar = new ICalendar()
    icalendar.setMethod("PUBLISH")
    // Cancel event
    icalendar.addEvent(biweeklyEvent(
      Some(uid),
      sequence,
      cancelIcal,
      recurUntil
    ))
    // Replacement event
    icalendar.addEvent(biweeklyEvent(
      None,
      sequence,
      replaceIcal,
      None
    ))
    icalendar
  }

  // Create biweekly ical for task
  def biweeklyTaskIcal(
    uid: String,
    sequence: Int,
    task: Task,
    subject: Subject,
    user: User,
    delete: Boolean = false
  ): ICalendar = {
    val icalendar = new ICalendar()
    icalendar.setMethod("PUBLISH")
    val event = new VEvent()
    event.setUid(new Uid(uid))
    event.setOrganizer(config.get[String]("play.mailer.user"))
    event.setSequence(sequence)
    event.setSummary(s"${subject.code} ${task.title}")
    val formatter = new SimpleDateFormat("yyyy-MM-dd")
    val dateStart =  new DateStart(formatter.parse(task.dueDate), false)
    event.setDateStart(dateStart)
    val description =
      if (task.description.isEmpty) "(No description)" else task.description
    event.setDescription(
      s"""
        |${subject.title.get} (${subject.code})
        |
        |${task.title}
        |Score: ${task.score}%
        |Due: ${task.dueDate}
        |
        |${description}
        |
        |${user.name} (${user.email})
      """.stripMargin.trim,
    )
    if (delete) {
      event.setStatus(Status.cancelled())
    }
    icalendar.addEvent(event)
    icalendar
  }

  // Biweekly event
  def biweeklyEvent(
    maybeUid: Option[Uid],
    sequence: Int,
    ical: Ical,
    recurUntil: Option[String]
  ): VEvent = {
    val event = new VEvent()
    maybeUid match {
      case Some(uid) =>
        event.setUid(uid)
      case None =>
        event.setUid(Uid.random())
    }
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
          val exceptionDateTime = s"${exceptionDate} ${ical.startTime}"
          exceptionDates.getValues().add(
            new ICalDate(formatter.parse(exceptionDateTime), true)
          )
        }
        event.addExceptionDates(exceptionDates)
      }
    }
    event
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

  // Check if given date is within range
  def dateInRange(date: String, start: String, end: String): Boolean = {
    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    val jodaDate = formatter.parseDateTime(date)
    val jodaStart = formatter.parseDateTime(start)
    val jodaEnd = formatter.parseDateTime(end)
    if (jodaDate >= jodaStart && jodaDate <= jodaEnd) {
      true
    } else {
      false
    }
  }

  // Append Asia/Kuala_Lumpur timezone to date
  def appendTimezone(date: String): String = {
    date + "+08:00"
  }

  // Calculate duration in minutes given start and end times
  def duration(start: String, end: String): Int = {
    val formatter = DateTimeFormat.forPattern("hh:mmaa")
    val jodaStart = formatter.parseDateTime(start)
    val jodaEnd = formatter.parseDateTime(end)
    (jodaStart to jodaEnd).duration.getStandardMinutes.toInt
  }

  // Check if both classes clash in time
  def clash(
    start1: String,
    end1: String,
    start2: String,
    end2: String
  ): Boolean = {
    val formatter = DateTimeFormat.forPattern("hh:mmaa")
    val jodaStart1 = formatter.parseDateTime(start1)
    val jodaEnd1 = formatter.parseDateTime(end1)
    val jodaStart2 = formatter.parseDateTime(start2)
    val jodaEnd2 = formatter.parseDateTime(end2)
    if (start1 < start2) {
      // Class 1 is before class 2
      if (start2 >= end1) {
        false
      } else {
        true
      }
    } else if (start1 > start2) {
      // Class 1 is after class 2
      if (start1 >= end2) {
        false
      } else {
        true
      }
    } else  {
      // Class 1 starts the same time as class 2
      true
    }
  }

  // Return database time strings for start and end
  def databaseTimes(time: String): (String, String) = {
    val times = time.split("-").map(_.trim)
    (times(0), times(1))
  }

  // Convert moment time to database time
  def unmomentTime(time: String): String = {
    twelveHour(time.split("T")(1).substring(0, 5))
  }

  // Get list of distinct students given list of classes
  def subjectStudents(classes: List[Class]) = {
    val students = classes.foldLeft(List[String]()) { (list, class_) =>
      list ++ class_.students
    }.distinct
    students
  }

  // Get list of times for availability table
  def tableTimes(): List[String] = {
    val inputFormatter = DateTimeFormat.forPattern("hh:mmaa")
    val outputFormatter = DateTimeFormat.forPattern("HH:mm")
    val start = inputFormatter.parseDateTime("08:00AM")
    val end = inputFormatter.parseDateTime("06:00PM")
    val interval = 30.minutes
    val times = ListBuffer[DateTime]()
    var time = start
    while (time < end) {
      times += time
      time += interval
    }
    val timeStrings = times.map(_.toString(outputFormatter)).toList
    timeStrings
  }

}
