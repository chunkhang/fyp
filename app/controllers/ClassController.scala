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
import models._

class ClassController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
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

  class ClassRequest[A](
    val classItem: Class,
    val subjectItem: Subject,
    request: UserRequest[A]
  ) extends WrappedRequest[A](request) {
    def user = request.user
  }

  def ClassAction(id: BSONObjectID)(implicit ec: ExecutionContext) =
    new ActionRefiner[UserRequest, ClassRequest] {
    def executionContext = ec
    def refine[A](input: UserRequest[A]) = {
      getClassWithSubject(id).map { maybeTuple =>
        maybeTuple
          .map(tuple => new ClassRequest(tuple._1, tuple._2, input))
          .toRight(
            Redirect(routes.ClassController.index())
              .flashing("danger" -> "Class not found")
          )
      }
    }
  }

  def PermittedAction(implicit ec: ExecutionContext) =
    new ActionFilter[ClassRequest] {
    def executionContext = ec
    def filter[A](input: ClassRequest[A]) = {
      subjectRepo.read(input.classItem.subjectId).map { maybeSubject =>
        maybeSubject.map { subject =>
          if (subject.userId != input.user._id.get) {
            Some(
              Redirect(routes.ClassController.index())
                .flashing("danger" -> "Not your class")
            )
          } else {
            None
          }
        } getOrElse {
          Some(
            Redirect(routes.ClassController.index())
              .flashing("danger" -> "Not your class")
          )
        }
      }
    }
  }

  def index = userAction.async { implicit request =>
    val email = request.user.email
    getClasses(email).map { maybeClasses =>
      maybeClasses match {
        case Some(classes) =>
          Ok(views.html.classes.index(classes))
        case None =>
          Ok(views.html.classes.index(ListMap()))
      }
    }
  }

  def view(id: BSONObjectID) =
    (userAction andThen ClassAction(id) andThen PermittedAction) {
      implicit request =>
        Ok(views.html.classes.view(request.subjectItem, request.classItem))
  }

  def edit(id: BSONObjectID) =
    (userAction andThen ClassAction(id) andThen PermittedAction) {
      implicit request =>
        Ok("Hello!")
  }

  def update(id: BSONObjectID) =
    (userAction andThen ClassAction(id) andThen PermittedAction) {
      implicit request =>
        Ok("Hello!")
  }

  def fetch = userAction.async { implicit request =>
    val email = request.user.email
    fetchClasses(email).flatMap { maybeResult =>
      maybeResult match {
        case Some((semester, subjects)) =>
          // Check if database already has classes under user
          getClasses(email).flatMap { maybeClasses =>
            maybeClasses match {
              case Some(classes) =>
                Future {
                  Ok(Json.obj(
                    "status" -> "info",
                    "message" -> "No new classes"
                  ))
                }
              case None =>
                saveClasses(email, semester, subjects).map { _ =>
                  Ok(Json.obj(
                    "status" -> "success",
                    "message" -> "New classes fetched"
                  ))
                }
            }
          }
        case None =>
          Future {
            Ok(Json.obj(
              "status" -> "error",
              "message" -> "Email not found"
            ))
          }
      }
    }
  }

  // Get saved classes from database
  def getClasses(email: String):
    Future[Option[ListMap[Subject, List[Class]]]] = {
    (for {
      // Get user id
      userId <- userRepo.findUserByEmail(email).map { maybeUser =>
        maybeUser.get._id.get
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

  // Get class and subject using class id
  def getClassWithSubject(classId: BSONObjectID):
    Future[Option[(Class, Subject)]] = {
    (for {
      // Get class
      class_ <- classRepo.read(classId).map { maybeClass =>
        maybeClass.get
      }
      // Get subject of class
      subject <- subjectRepo.read(class_.subjectId).map { maybeSubject =>
        maybeSubject.get
      }
    } yield Some(class_, subject)) fallbackTo Future(None)
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
      subjectIdMap <- userRepo.findUserByEmail(email).flatMap { maybeUser =>
        var subjectIdMap = Map[BSONObjectID, List[JsonClass]]()
        Future.traverse(activeSubjects) { subject =>
          val subjectId = BSONObjectID.generate
          subjectRepo.create(Subject(
            _id = Option(subjectId),
            code = subject.code,
            semester = activeSemester,
            userId = maybeUser.get._id.get
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
