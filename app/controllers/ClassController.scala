package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable.Map
import scala.collection.immutable.ListMap
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.json.Json
import play.api.{Logger, Configuration}
import reactivemongo.bson.BSONObjectID
import actions._
import models._

class ClassController @Inject()(
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
    getClasses(request.email).map { maybeClasses =>
      maybeClasses match {
        case Some(classes) =>
          Ok(views.html.classes.index(classes))
        case None =>
          Ok(views.html.classes.index(ListMap()))
      }
    }
  }

  def fetch = authenticatedAction.async { implicit request =>
    fetchClasses(request.email).map { maybeResult =>
      maybeResult match {
        case Some((semester, subjects)) =>
          // TODO: Only save classes if they are new
          Ok(Json.obj(
            "status" -> "success",
            "semester" -> semester,
            "subjects" -> subjects.length
          ))
          // saveClasses(request.email, semester, subjects).map { _ =>
          // }
        case None =>
          Ok(Json.obj(
            "status" -> "fail",
            "reason" -> "Email not found"
          ))
      }
    }
  }

  // Get saved classes from database
  def getClasses(email: String):
    Future[Option[ListMap[Subject, List[Class]]]] = {
    (for {
      // Get user id
      userId <- userRepo.findUserByEmail(email).map { user =>
        user.get._id.get
      }
      // List subjects under user
      subjects <- subjectRepo.findSubjectsByUserId(userId).map { subjects =>
        if (subjects.isEmpty) {
          throw new Exception("no subjects found")
        }
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
      // Sort map
      sortedMap <- Future {
        // Sort subjects
        val sequenceWithSortedSubjects = subjectMap.toSeq.sortBy { item =>
          val subject = item._1
          subject.code
        }
        // Then sort classes
        val sortedSequence = sequenceWithSortedSubjects.map { item =>
          val (subject, classes) = item
          val sortedClasses = classes.sortBy { class_ =>
            (class_.category, class_.group)
          }
          (subject, sortedClasses)
        }
        ListMap(sortedSequence :_*)
      }
    } yield Some(sortedMap)) fallbackTo Future(None)
  }

  // Fetch latest active classes from API
  def fetchClasses(email: String):
    Future[Option[(String, List[JsonSubject])]] = {
    // GET request to fetch active subjects
    ws.url(config.get[String]("my.api.icheckin.classUrl"))
      .addQueryStringParameters("email" -> email)
      .get().map { response =>
        val semester = (response.json \ "semester").as[String]
        val subjects = (response.json \ "subjects").as[List[JsonSubject]]
        semester match {
          case "" =>
            // Email not found
            None
          case _ =>
            Some((semester, subjects))
        }
      }
  }

  // Save classes to database
  def saveClasses(
    email: String,
    activeSemester: String,
    activeSubjects: List[JsonSubject]
  ): Future[Unit] = {
    for {
      // Create subjects under user
      subjectIdMap <- userRepo.findUserByEmail(email).flatMap { user =>
        var subjectIdMap = Map[BSONObjectID, List[JsonClass]]()
        Future.traverse(activeSubjects) { subject =>
          val subjectId = BSONObjectID.generate
          subjectRepo.create(Subject(
            _id = Option(subjectId),
            code = subject.code,
            semester = activeSemester,
            userId = user.get._id.get
          )).map { _ =>
            Logger.info(
              s"Created Subject(${subject.code}, ${activeSemester}, " +
              s"${subjectId})"
            )
            subjectIdMap += (subjectId -> subject.classes)
          }
        }.map { _ =>
          subjectIdMap
        }
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
