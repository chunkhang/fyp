package models

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._

case class Class(
  _id: Option[BSONObjectID] = None,
  category: String,
  day: Int,
  time: String,
  duration: Int,
  venue: String,
  students: List[String]
) extends Entity

class ClassRepository @Inject()(
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) extends Repository[Class] {

  val collectionName = "classes"
  implicit val documentFormat = Json.format[Class]

}
