package models

import play.api.libs.json._
import slick.driver.PostgresDriver.api._
import java.sql.Timestamp
import java.text.SimpleDateFormat

case class Person(id: Long, name: String, age: Int, created: Timestamp, updated: Timestamp)

object Person {
  implicit object timestampFormat extends Format[Timestamp] {
    val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'")
    def reads(json: JsValue) = {
      val str = json.as[String]
      JsSuccess(new Timestamp(format.parse(str).getTime))
    }
    def writes(ts: Timestamp) = JsString(format.format(ts))
  }

  implicit val personFormat = Json.format[Person]
}
