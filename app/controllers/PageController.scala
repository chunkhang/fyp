package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.json.Json
import play.api.Logger
import play.api.Configuration
import reactivemongo.bson.BSONObjectID
import actions.AuthenticatedAction
import models._

class PageController @Inject()(
  cc: ControllerComponents,
  authenticatedAction: AuthenticatedAction,
  ws: WSClient,
  config: Configuration,
  userRepo: UserRepository,
  subjectRepo: SubjectRepository,
  classRepo: ClassRepository
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  // JSON data types
  case class JsonSubject(
    code: String,
    classes: List[JsonClass]
  )
  case class JsonClass(
    category: String,
    group: Int,
    students: List[String]
  )
  implicit val classReader = Json.reads[JsonClass]
  implicit val subjectReader = Json.reads[JsonSubject]

  def index = authenticatedAction.async { implicit request =>
    // GET request to fetch active classes
    ws.url(config.get[String]("my.api.icheckin.classUrl"))
      .addQueryStringParameters("email" -> "nnurl@sunway.edu.my")
      .get()
      .flatMap { response =>
        userRepo.findByEmail(request.email).map { user =>
          val semester_ = (response.json \ "semester").as[String]
          val subjects = (response.json \ "subjects").as[List[JsonSubject]]
          // Create subjects under user
          subjects.foreach { subject =>
            val subjectId_ = BSONObjectID.generate
            subjectRepo.create(Subject(
              _id = subjectId_,
              code = subject.code,
              semester = semester_,
              userId = user.get._id
            )).map { _ =>
              Logger.info(s"Created Subject(${subject.code}, ${semester_})")
              // Create classes under subject
              subject.classes.foreach { class_ =>
                classRepo.create(Class(
                  _id = BSONObjectID.generate,
                  category = class_.category,
                  group = class_.group,
                  students = class_.students,
                  subjectId = subjectId_,
                )).map { _ =>
                  Logger.info(
                    s"Created Class(${subject.code}, ${semester_}, " +
                    s"${class_.category}, ${class_.group})"
                  )
                }
              }
            }
          }
          Ok(views.html.index(request.name, request.email))
        }
      }
  }

  def login = Action { implicit request =>
    try {
      request.session("accessToken")
      request.session("name")
      request.session("email")
      Redirect(routes.PageController.index())
    } catch {
      case e: NoSuchElementException =>
        Ok(views.html.login())
    }
  }

}
