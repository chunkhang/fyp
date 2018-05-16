package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import models.JsonFormats._
import models.{User, UserRepository}
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import reactivemongo.bson.BSONObjectID
import actions.AuthenticatedAction

class UserController @Inject()(
  cc: ControllerComponents,
  userRepo: UserRepository,
  authenticatedAction: AuthenticatedAction
) extends AbstractController(cc) {

  def getAllUsers = authenticatedAction.async { implicit request =>
    userRepo.getAll.map { users =>
      Ok(Json.toJson(users))
    }
  }

  def getUser(id: BSONObjectID) = authenticatedAction.async {
    implicit request =>
      userRepo.getUser(id).map { maybeUser =>
        maybeUser.map { user =>
          Ok(Json.toJson(user))
        }.getOrElse(NotFound)
      }
  }

  def createUser() = authenticatedAction.async(parse.json) {
    implicit request =>
      request.body.validate[User].map { user =>
        userRepo.addUser(user).map { _ =>
          Created
        }
      }.getOrElse(Future.successful(BadRequest("Invalid User format")))
  }

  def updateUser(id: BSONObjectID) = authenticatedAction.async(parse.json) {
    implicit request =>
      request.body.validate[User].map { user =>
        userRepo.updateUser(id, user).map {
          case Some(user) => Ok(Json.toJson(user))
          case None => NotFound
        }
      }.getOrElse(Future.successful(BadRequest("Invalid Json")))
  }

  def deleteUser(id: BSONObjectID) = authenticatedAction.async {
    implicit request =>
      userRepo.deleteUser(id).map {
        case Some(user) => Ok(Json.toJson(user))
        case None => NotFound
      }
  }

}
