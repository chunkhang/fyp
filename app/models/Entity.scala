package models

import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.libs.json.{OFormat, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

trait Entity {
  def _id: BSONObjectID
}

abstract class Repository[T](
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) {

  val collectionName: String
  implicit val documentFormat: OFormat[T]

  def collection: Future[JSONCollection] =
    reactiveMongoApi.database.map(_.collection(collectionName))

  def create(entity: T): Future[WriteResult] = {
    collection.flatMap(_.insert(entity))
  }

  def read(id: BSONObjectID): Future[Option[T]] = {
    val query = BSONDocument("_id" -> id)
    collection.flatMap(_
      .find(query)
      .one[T]
    )
  }

  def readAll(): Future[List[T]] = {
    val query = Json.obj()
    collection.flatMap(_
      .find(query)
      .cursor[T]()
      .collect[List](config.get[Int]("my.db.maxDocuments"),
        Cursor.FailOnError[List[T]]())
    )
  }

  def update(id: BSONObjectID, entity: T): Future[Option[T]] = {
    val selector = BSONDocument("_id" -> id)
    val document = Json.toJson(entity).as[BSONDocument]
    val updateModifier = BSONDocument(
      "$set" -> document
    )
    collection.flatMap(_
      .findAndUpdate(selector, updateModifier, fetchNewObject = true)
      .map(_.result[T])
    )
  }

  def delete(id: BSONObjectID): Future[Option[T]] = {
    val selector = BSONDocument("_id" -> id)
    collection.flatMap(_
      .findAndRemove(selector)
      .map(_.result[T])
    )
  }

}
