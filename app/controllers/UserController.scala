package controllers

import javax.inject._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}

// For Form
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

// Model
import models._

// Define Form Case Class
case class UserData(name: String, email: String, password: String)

// User Controller
class UserController @Inject()(repo: UserRepository,
                               cc: MessagesControllerComponents)
                              (implicit ec: ExecutionContext)
extends MessagesAbstractController(cc) {

  // Define Form Structure
  val userForm = Form {
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "password" -> nonEmptyText(minLength = 6)
    )(UserData.apply)(UserData.unapply)
  }

  def index = Action { implicit request =>
    Ok(views.html.user.index())
  }

  def newUser = Action { implicit request =>
    Ok(views.html.user.newUser(userForm))
  }

  def createUser = Action.async { implicit request =>
    userForm.bindFromRequest.fold(
      // If Error
      errorForm => {
        Future.successful(Ok(views.html.user.newUser(errorForm)))
      },
      // Success
      user => {
        repo.create(user.name, user.email, user.password).map { _ =>
          Redirect(routes.UserController.index).flashing("success" -> "Succesfully sign up")
        }
      }
    )
  }

}

