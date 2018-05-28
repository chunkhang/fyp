package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable.Map
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

  def index = authenticatedAction.async { implicit request =>
    fetchClasses(request.email).map { classes =>
      Ok(views.html.index(request.name, request.email, classes))
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

  // Fetch saved classes from database
  def fetchClasses(email: String): Future[Map[Subject, List[Class]]] = {
    for {
      // Get user id
      userId <- userRepo.findUserByEmail(email).map { user =>
        user.get._id.get
      }
      // List subjects under user
      subjects <- subjectRepo.findSubjectsByUserId(userId).map { subjects =>
        subjects
      }
      // Map subject to classes
      subjectMap <- {
        var subjectMap = Map[Subject, List[Class]]()
        Future.traverse(subjects) { subject =>
          classRepo.findClassesBySubjectId(subject._id.get).map { classes =>
            subjectMap += (subject -> classes)
          }
        }.map { _ =>
          subjectMap
        }
      }
    } yield subjectMap
  }

  // Update database with current active classes
  def refreshClasses(email: String): Future[Unit] = {
    for {
      // GET request to fetch active subjects
      active <- ws.url(config.get[String]("my.api.icheckin.classUrl"))
                  .addQueryStringParameters("email" -> "nnurl@sunway.edu.my")
                  .get().map { response =>
        val semester = (response.json \ "semester").as[String]
        val subjects = (response.json \ "subjects").as[List[JsonSubject]]
        (semester, subjects)
      }
      // Create subjects under user
      subjectIdMap <- userRepo.findUserByEmail(email).map { user =>
        var subjectIdMap = Map[BSONObjectID, List[JsonClass]]()
        val semester_ = active._1
        val subjects = active._2
        subjects.foreach { subject =>
          val subjectId = BSONObjectID.generate
          subjectRepo.create(Subject(
            _id = Option(subjectId),
            code = subject.code,
            semester = semester_,
            userId = user.get._id.get
          )).map { _ =>
            Logger.info(
              s"Created Subject(${subject.code}, ${semester_}, " +
              s"${subjectId})"
            )
            subjectIdMap += (subjectId -> subject.classes)
          }
        }
        subjectIdMap
      }
      // Create classes under subject
      _ <- Future {
        subjectIdMap.foreach { case(subjectId_, classes) =>
          classes.foreach { class_ =>
            classRepo.create(Class(
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
      }
    } yield Unit
  }

}
