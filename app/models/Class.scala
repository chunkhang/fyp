package models

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._

case class Class(
  _id: Option[BSONObjectID] = None,
  category: String,
  group: Int,
  students: List[String],
  day: Option[String] = None,
  startTime: Option[String] = None,
  endTime: Option[String] = None,
  uid: Option[String] = None,
  sequence: Option[Int] = None,
  venueId: Option[BSONObjectID] = None,
  subjectId: BSONObjectID
) extends Entity

class ClassRepository @Inject()(
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) extends Repository[Class] {

  val collectionName = "classes"
  implicit val documentFormat = Json.format[Class]

  def findClassesBySubjectId(subjectId: BSONObjectID): Future[List[Class]] = {
    val query = Json.obj("subjectId" -> subjectId)
    collection.flatMap(_
      .find(query)
      .cursor[Class]()
      .collect[List](config.get[Int]("my.db.maxDocuments"),
        Cursor.FailOnError[List[Class]]())
    )
  }

}
