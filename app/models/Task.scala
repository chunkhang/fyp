package models

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._

case class Task(
  _id: Option[BSONObjectID] = None,
  title: String,
  score: Int,
  dueDate: String,
  description: String,
  subjectId: BSONObjectID
) extends Entity

class TaskRepository @Inject()(
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) extends Repository[Task] {

  val collectionName = "tasks"
  implicit val documentFormat = Json.format[Task]

}
