package models

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

/* Document */

case class User(
  _id: Option[BSONObjectID],
  name: String,
  email: String,
  refreshToken: String
)

object JsonFormats {
  import play.api.libs.json._

  implicit val userFormat: OFormat[User] = Json.format[User]
}

class UserRepository @Inject()(
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) {
  import JsonFormats._

  /* Collection */

  def collection: Future[JSONCollection] =
    reactiveMongoApi.database.map(_.collection("users"))

  /* CRUD */

  def getAll(): Future[List[User]] = {
    val query = Json.obj()
    collection.flatMap(_
      .find(query)
      .cursor[User]()
      .collect[List](config.get[Int]("my.queryLimit"),
        Cursor.FailOnError[List[User]]())
    )
  }

  def getById(id: BSONObjectID): Future[Option[User]] = {
    val query = BSONDocument("_id" -> id)
    collection.flatMap(_
      .find(query)
      .one[User]
    )
  }

  def save(user: User): Future[WriteResult] = {
    collection.flatMap(_.insert(user))
  }

  def updateById(id: BSONObjectID, user: User): Future[Option[User]] = {
    val selector = BSONDocument("_id" -> id)
    val updateModifier = BSONDocument(
      "$set" -> BSONDocument(
        "name" -> user.name,
        "email" -> user.email,
        "refreshToken" -> user.refreshToken
      )
    )
    collection.flatMap(_
      .findAndUpdate(selector, updateModifier, fetchNewObject = true)
      .map(_.result[User])
    )
  }

  def deleteById(id: BSONObjectID): Future[Option[User]] = {
    val selector = BSONDocument("_id" -> id)
    collection.flatMap(_
      .findAndRemove(selector)
      .map(_.result[User])
    )
  }

  /* Queries */

  def findByName(name: String): Future[List[User]] = {
    val query = Json.obj("name" -> name)
    collection.flatMap(_
      .find(query)
      .cursor[User]()
      .collect[List](config.get[Int]("my.queryLimit"),
        Cursor.FailOnError[List[User]]())
    )
  }

}
