package utils

import java.text.SimpleDateFormat

class Utils {

  // Append url with query params
  def urlWithParams(url: String, params: Map[String, String]) = {
    url + "?" + params.foldLeft("")( (acc, kv) =>
      acc + "&" + kv._1 + "=" + kv._2
    ).substring(1)
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

  // Convert time string to integer for comparison
  def timeInteger(time: String): Int = {
    // Convert to 24-hour format
    val inputTimeFormat = new SimpleDateFormat("hh:mma")
    val outputTimeFormat = new SimpleDateFormat("HH:mm")
    val timeObject = inputTimeFormat.parse(time)
    val convertedTime = outputTimeFormat.format(timeObject)
    // Convert to total minutes
    val hours = convertedTime.slice(0, 2).toInt
    val minutes = convertedTime.slice(3, 5).toInt
    (hours * 60) + minutes
  }

  // Convert date string to integer for comparison
  def dateInteger(date: String): Int = {
    // Convert to total days
    val years = date.slice(0, 4).toInt
    val months = date.slice(5, 7).toInt
    val days = date.slice(8, 10).toInt
    (years * 365) + (months * 30) + days
  }

}
