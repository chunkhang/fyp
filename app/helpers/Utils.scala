package helpers

import scala.collection.mutable.ListBuffer
import com.github.nscala_time.time.Imports._

class Utils {

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

}
