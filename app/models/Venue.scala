package models

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._

case class Venue(
  _id: Option[BSONObjectID] = None,
  name: String,
  building: String
) extends Entity

class VenueRepository @Inject()(
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) extends Repository[Venue] {

  val collectionName = "venues"
  implicit val documentFormat = Json.format[Venue]

}
