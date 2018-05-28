package models

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._

case class User(
  _id: Option[BSONObjectID] = None,
  name: String,
  email: String,
  refreshToken: String
) extends Entity

class UserRepository @Inject()(
  implicit ec: ExecutionContext,
  reactiveMongoApi: ReactiveMongoApi,
  config: Configuration
) extends Repository[User] {

  val collectionName = "users"
  implicit val documentFormat = Json.format[User]

  def findUserByEmail(email: String): Future[Option[User]] = {
    val query = Json.obj("email" -> email)
    collection.flatMap(_
      .find(query)
      .one[User]
    )
  }

}
