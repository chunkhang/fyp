package models

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._

case class Event(
  _id: Option[BSONObjectID] = None,
  uid: String,
  summary: String,
  dateStart: String,
  dateEnd: String,
  location: String,
  description: String,
  recurrenceDates: List[String],
  classId: BSONObjectID
) extends Entity

class EventRepository @Inject()(
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) extends Repository[Event] {

  val collectionName = "events"
  implicit val documentFormat = Json.format[Event]

}
