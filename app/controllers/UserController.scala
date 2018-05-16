package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import models.JsonFormats._
import models.{User, UserRepository}
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import reactivemongo.bson.BSONObjectID

class UserController @Inject()(
  cc: ControllerComponents,
  userRepo: UserRepository
) extends AbstractController(cc) {

  def getAllUsers = Action.async {
    userRepo.getAll.map{ users =>
      Ok(Json.toJson(users))
    }
  }

  def getUser(userId: BSONObjectID) = Action.async { req =>
    userRepo.getUser(userId).map { maybeUser =>
      maybeUser.map { user =>
        Ok(Json.toJson(user))
      }.getOrElse(NotFound)
    }
  }

  def createUser() = Action.async(parse.json) { req =>
    req.body.validate[User].map { user =>
      userRepo.addUser(user).map { _ =>
        Created
      }
    }.getOrElse(Future.successful(BadRequest("Invalid User format")))
  }

  def updateUser(userId: BSONObjectID) = Action.async(parse.json) { req =>
    req.body.validate[User].map { user =>
      userRepo.updateUser(userId, user).map {
        case Some(user) => Ok(Json.toJson(user))
        case None => NotFound
      }
    }.getOrElse(Future.successful(BadRequest("Invalid Json")))
  }

  def deleteUser(userId: BSONObjectID) = Action.async { req =>
    userRepo.deleteUser(userId).map {
      case Some(user) => Ok(Json.toJson(user))
      case None => NotFound
    }
  }

}
