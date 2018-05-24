package models

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._

case class Subject(
  _id: Option[BSONObjectID] = None,
  code: String,
  semester: String,
  userId: BSONObjectID
) extends Entity

class SubjectRepository @Inject()(
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) extends Repository[Subject] {

  val collectionName = "subjects"
  implicit val documentFormat = Json.format[Subject]

}
