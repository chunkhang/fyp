package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
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

  case class JsonSubject(code: String, classes: List[JsonClass])
  case class JsonClass(category: String, group: Int, students: List[String])
  implicit val classReader = Json.reads[JsonClass]
  implicit val subjectReader = Json.reads[JsonSubject]
  case class ResultSubjects(semester: String, subjects: List[JsonSubject])
  case class ResultClasses(classMap: Map[BSONObjectID, List[JsonClass]])

  def index = authenticatedAction.async { implicit request =>
    val result = for {
      // GET request to fetch active subjects
      r1 <- ws.url(config.get[String]("my.api.icheckin.classUrl"))
              .addQueryStringParameters("email" -> "nnurl@sunway.edu.my")
              .get().map { response =>
        ResultSubjects(
          semester = (response.json \ "semester").as[String],
          subjects = (response.json \ "subjects").as[List[JsonSubject]]
        )
      }
      // Create subjects under user
      r2 <- userRepo.findByEmail(request.email).flatMap { user =>
        val createSubjects: List[Future[Map[BSONObjectID, List[JsonClass]]]] =
          for (subject <- r1.subjects) yield {
            val subjectId = BSONObjectID.generate
            subjectRepo.create(Subject(
              _id = subjectId,
              code = subject.code,
              semester = r1.semester,
              userId = user.get._id
            )).map { _ =>
              Logger.info(
                s"Created Subject(${subject.code}, ${r1.semester}, " +
                s"${subjectId})"
              )
              Map(subjectId -> subject.classes)
            }
          }
        Future.sequence(createSubjects).map { mapList =>
          ResultClasses(
            classMap = mapList.flatten.toMap
          )
        }
      }
      // Create classes under subject
      r3 <- Future(
        r2.classMap.foreach { case (subjectId_, classes) =>
          classes.foreach { class_ =>
            classRepo.create(Class(
              _id = BSONObjectID.generate,
              category = class_.category,
              group = class_.group,
              students = class_.students,
              subjectId = subjectId_
            )).map { _ =>
              Logger.info(
                s"Created Class(${subjectId_}, ${class_.category}, " +
                s"${class_.group})"
              )
            }
          }
        }
      )
    } yield Unit
    result.map { _ =>
      Ok(views.html.index(request.name, request.email))
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
