package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import scala.collection.mutable.{Map, ListBuffer}
import scala.collection.immutable.ListMap
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.ws._
import play.api.libs.json.Json
import play.api.{Logger, Configuration}
import reactivemongo.bson.BSONObjectID
import biweekly._
import biweekly.property._
import models._
import helpers.Utils
import mailer.Mailer

case class ClassData(
  day: String,
  startTime: String,
  endTime: String,
  venue: String
)

class ClassController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  ws: WSClient,
  config: Configuration,
  userRepo: UserRepository,
  subjectRepo: SubjectRepository,
  classRepo: ClassRepository,
  venueRepo: VenueRepository,
  utils: Utils,
  mailer: Mailer
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) with play.api.i18n.I18nSupport {

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

  def validateTimes(startTime: String, endTime: String) = {
    val start = utils.totalMinutes(startTime)
    val end = utils.totalMinutes(endTime)
    if (end > start) {
      Some(startTime, endTime)
    } else {
      None
    }
  }

  def validateHours(startTime: String, endTime: String) = {
    val start = utils.totalMinutes(startTime)
    val end = utils.totalMinutes(endTime)
    val min = utils.totalMinutes("08:00AM")
    val max = utils.totalMinutes("06:00PM")
    if (start >= min && end <= max) {
      Some(startTime, endTime)
    } else {
      None
    }
  }

  def validateDuration(startTime: String, endTime: String) = {
    val start = utils.totalMinutes(startTime)
    val end = utils.totalMinutes(endTime)
    val duration = end - start
    if (duration >= 60 && duration <= 180) {
      Some(startTime, endTime)
    } else {
      None
    }
  }

  def validateVenue(venue: String) = {
    val result = venueRepo.readAll().map { venues =>
      val venueNames = venues.map { venue_ =>
        venue_.name + ", " + venue_.building
      }
      if (venueNames contains venue) {
        Some(venue)
      } else {
        None
      }
    }
    Await.result(result, 5.seconds)
  }

  val classForm = Form(
    mapping(
      "Day" -> nonEmptyText,
      "Start Time" -> nonEmptyText,
      "End Time" -> nonEmptyText,
      "Venue" -> nonEmptyText
      )(ClassData.apply)(ClassData.unapply)
      verifying(
        "End time must be later than start time",
        fields => fields match {
          case classData =>
            validateTimes(classData.startTime, classData.endTime).isDefined
        }
      )
      verifying(
        "Class hours: 08:00AM - 06:00PM",
        fields => fields match {
          case classData =>
            validateHours(classData.startTime, classData.endTime).isDefined
        }
      )
      verifying(
        "Class duration: 1 - 3 hours",
        fields => fields match {
          case classData =>
            validateDuration(classData.startTime, classData.endTime).isDefined
        }
      )
      verifying(
        "Invalid venue",
        fields => fields match {
          case classData =>
            validateVenue(classData.venue).isDefined
        }
      )
  )

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

  def edit(id: BSONObjectID) =
    (userAction andThen ClassAction(id) andThen PermittedAction).async {
      implicit request =>
        val daySelections = getDaySelections()
        getVenueSelections().flatMap { venueSelections =>
          request.classItem.day match {
            case Some(day) =>
              getVenueName(request.classItem.venueId.get).map { venueName =>
                val filledForm = classForm.fill(ClassData(
                  day = request.classItem.day.get,
                  startTime = request.classItem.startTime.get,
                  endTime = request.classItem.endTime.get,
                  venue = venueName
                ))
                Ok(views.html.classes.edit(
                  request.subjectItem,
                  request.classItem,
                  daySelections,
                  venueSelections,
                  filledForm
                ))
              }
            case None =>
              Future {
                Ok(views.html.classes.edit(
                  request.subjectItem,
                  request.classItem,
                  daySelections,
                  venueSelections,
                  classForm
                ))
              }
          }
        }
  }

  def update(id: BSONObjectID) =
    (userAction andThen ClassAction(id) andThen PermittedAction).async {
      implicit request =>
        val daySelections = getDaySelections()
        getVenueSelections().flatMap { venueSelections =>
          classForm.bindFromRequest.fold(
            formWithErrors => {
              Future {
                BadRequest(
                  views.html.classes.edit(
                    request.subjectItem,
                    request.classItem,
                    daySelections,
                    venueSelections,
                    formWithErrors
                  )
                )
              }
            },
            classData => {
              // Update class in database
              getVenueId(classData.venue).flatMap { venueId_ =>
                classRepo.update(id, request.classItem.copy(
                  day = Some(classData.day),
                  startTime = Some(classData.startTime),
                  endTime = Some(classData.endTime),
                  venueId = Some(venueId_)
                )).map { _ =>
                  Logger.info(s"Updated Class(${id})")
                  getClassWithSubject(id).map { maybeTuple =>
                    val (class_, subject) = maybeTuple.get
                    // Create ical
                    val classIcal = utils.classIcal(
                      request.user,
                      subject,
                      class_,
                      classData.venue
                    )
                    var sequence_ = 0
                    var biweeklyIcal = new ICalendar()
                    var emailSubject =
                      s": ${subject.title.get} (${class_.category})"
                    class_.uid match {
                      case Some(uid_) =>
                        // Update ical
                        sequence_ = class_.sequence.get + 1
                        biweeklyIcal = utils.biweeklyIcal(
                          method= "Request",
                          uid = new Uid(uid_),
                          sequence = sequence_,
                          ical = classIcal,
                          recurUntil = Some(subject.endDate.get)
                        )
                        emailSubject = "Changes to Class" + emailSubject
                      case None =>
                        // Add ical
                        val uid_ = Uid.random()
                        biweeklyIcal = utils.biweeklyIcal(
                          method= "Publish",
                          uid = uid_,
                          sequence = sequence_,
                          ical = classIcal,
                          recurUntil = Some(subject.endDate.get)
                        )
                        emailSubject = "New Class" + emailSubject
                        // Save uid
                        classRepo.update(id, class_.copy(
                          uid = Some(uid_.getValue())
                        )).map { _ =>
                          Logger.info(s"Updated Class(${id}, uid = ${uid_})")
                        }
                    }
                    // Update ical sequence in database
                    classRepo.update(id, class_.copy(
                      sequence = Some(sequence_)
                    )).map { _ =>
                      Logger.info(
                        s"Updated Class(${id}, sequence = ${sequence_})"
                      )
                    }
                    // Send ical to students
                    mailer.sendIcs(
                      subject = emailSubject,
                      toList = request.classItem.students.map { student =>
                        student + "@" + config.get[String]("my.domain.student")
                      },
                      lecturer = request.user.name,
                      ics = biweeklyIcal
                    )
                  }
                  Redirect(routes.ClassController.index())
                    .flashing("success" -> "Successfully edited class")
                }
              }
            }
          )
        }
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
    Future[Option[ListMap[Subject, List[(Class, Option[String])]]]] = {
    (for {
      // Get user id
      userId <- userRepo.findUserByEmail(email).map { maybeUser =>
        maybeUser.get._id.get
      }
      // List subjects under user
      subjects <- subjectRepo.findSubjectsByUserId(userId).map { subjects =>
        if (subjects.isEmpty) {
          throw new Exception("No subject found")
        }
        subjects
      }
      // Get classes with venues under each subject
      subjectTuples <- {
        var subjectTuples = ListBuffer[(Subject, List[Class])]()
        Future.traverse(subjects) { subject =>
          classRepo.findClassesBySubjectId(subject._id.get).map { classes =>
            // Sort students
            val sortedClasses = classes.map { class_ =>
              val sortedStudents = class_.students.sortWith(_ < _)
              class_.copy(students = sortedStudents)
            }
            val subjectTuple: (Subject, List[Class]) = (subject, sortedClasses)
            subjectTuples += subjectTuple
          }
        }.map { _ =>
          subjectTuples
        }
      }
      // Get venues for classes and create map
      subjectMap <- {
        var subjectMap = Map[Subject, List[(Class, Option[String])]]()
        Future.traverse(subjectTuples) { subjectTuple =>
          val (subject, classes) = subjectTuple
          Future.traverse(classes) { class_ =>
            if (class_.venueId.isDefined) {
              venueRepo.read(class_.venueId.get).map { maybeVenue =>
                Some(maybeVenue.get.name + ", " + maybeVenue.get.building)
              }
            } else {
              Future {
                None
              }
            }
          }.map { venues =>
            val classTuples = classes zip venues
            subjectMap += (subject -> classTuples)
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
          val (subject, classTuples) = item
          val sortedClassTuples = classTuples.sortBy { tuple =>
            val (class_, venue) = tuple
            (class_.category, class_.group)
          }
          (subject, sortedClassTuples)
        }
        ListMap(sortedSequence :_*)
      }
    } yield Some(sortedMap)) recover {
      case e: Exception =>
        Logger.warn(e.toString)
        None
    }
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
    } yield Some(class_, subject)) recover {
      case e: Exception =>
        Logger.warn(e.toString)
        None
    }
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

  // Selections for day form field
  def getDaySelections(): Seq[(String, String)] = {
    val days = Seq(
      "Monday",
      "Tuesday",
      "Wednesday",
      "Thursday",
      "Friday",
      "Saturday"
    )
    days zip days
  }

  // Selections for venue form field
  def getVenueSelections(): Future[Seq[String]] = {
    // Get all venues from database
    venueRepo.readAll().map { venues =>
      val venueTuples = venues.map { venue =>
        (venue.name, venue.building)
      }
      // Sort venues
      val universityVenues = venueTuples.filter { item =>
        item._2 == "Sunway University"
      } sortBy(item => item._1)
      val collegeVenues = venueTuples.filter { item =>
        item._2 == "Sunway College"
      } sortBy(item => item._1)
      val graduateVenues = venueTuples.filter { item =>
        item._2 == "Graduate Centre"
      } sortBy(item => item._1)
      val sortedVenues = universityVenues ++ collegeVenues ++ graduateVenues
      sortedVenues.map { tuple =>
        val (name, building) = tuple
        name + ", " + building
      }
    }
  }

  // Get venue name given venue id
  def getVenueName(id: BSONObjectID): Future[String] = {
    venueRepo.read(id).map { maybeVenue =>
      maybeVenue.get.name + ", " + maybeVenue.get.building
    }
  }

  // Get venue id given venue name
  def getVenueId(name: String): Future[BSONObjectID] = {
    venueRepo.readAll().map { venues =>
      val venue = venues.filter { venue =>
        name == venue.name + ", " + venue.building
      }(0)
      venue._id.get
    }
  }

}
