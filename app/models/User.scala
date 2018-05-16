package models

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

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
  reactiveMongoApi: ReactiveMongoApi
) {
  import JsonFormats._

  def usersCollection: Future[JSONCollection] =
    reactiveMongoApi.database.map(_.collection("users"))

  def getAll: Future[Seq[User]] = {
    val query = Json.obj()
    usersCollection.flatMap(_.find(query)
      .cursor[User](ReadPreference.primary)
      .collect[Seq](100, Cursor.FailOnError[Seq[User]]())
    )
  }

  def getUser(id: BSONObjectID): Future[Option[User]] = {
    val query = BSONDocument("_id" -> id)
    usersCollection.flatMap(_.find(query).one[User])
  }

  def addUser(user: User): Future[WriteResult] = {
    usersCollection.flatMap(_.insert(user))
  }

  def updateUser(id: BSONObjectID, user: User): Future[Option[User]] = {
    val selector = BSONDocument("_id" -> id)
    val updateModifier = BSONDocument(
      "$set" -> BSONDocument(
        "name" -> user.name,
        "email" -> user.email,
        "refreshToken" -> user.refreshToken
      )
    )
    usersCollection.flatMap(
      _.findAndUpdate(selector, updateModifier, fetchNewObject = true)
        .map(_.result[User])
    )
  }

  def deleteUser(id: BSONObjectID): Future[Option[User]] = {
    val selector = BSONDocument("_id" -> id)
    usersCollection.flatMap(_.findAndRemove(selector).map(_.result[User]))
  }

}
