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
  group: Int,
  students: List[String],
  day: Option[Int] = None,
  time: Option[String] = None,
  duration: Option[Int] = None,
  venue: Option[String] = None,
  subjectId: BSONObjectID
) extends Entity

class ClassRepository @Inject()(
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) extends Repository[Class] {

  val collectionName = "classes"
  implicit val documentFormat = Json.format[Class]

}
