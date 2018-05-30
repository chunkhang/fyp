package controllers

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.async.Async.{async, await}
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import actions._
import models._

class SubjectController @Inject()(
  cc: ControllerComponents,
  userAction: UserAction,
  userRepo: UserRepository,
  subjectRepo: SubjectRepository
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  class ItemRequest[A](
    val item: Subject,
    request: UserRequest[A]
  ) extends WrappedRequest[A](request) {
    def email = request.email
  }

  def ItemAction(itemId: BSONObjectID)(implicit ec: ExecutionContext) =
    new ActionRefiner[UserRequest, ItemRequest] {
    def executionContext = ec
    def refine[A](input: UserRequest[A]) = async {
      val maybeSubject = await(subjectRepo.read(itemId))
      maybeSubject
        .map(new ItemRequest(_, input))
        .toRight(
          Redirect(routes.ClassController.index())
            .flashing("message" -> "Subject not found")
        )
    }
  }

  def PermissionCheckAction(implicit ec: ExecutionContext) =
    new ActionFilter[ItemRequest] {
    def executionContext = ec
    def filter[A](input: ItemRequest[A]) = {
      userRepo.findUserByEmail(input.email).map { maybeUser =>
        maybeUser.flatMap { user =>
          if (input.item.userId == user._id.get) {
            None
          } else {
            Some(
              Redirect(routes.ClassController.index())
                .flashing("message" -> "Not allowed to view that subject")
            )
          }
        }
      }
    }
  }

  def edit(id: BSONObjectID) = (userAction andThen ItemAction(id) andThen PermissionCheckAction ) { implicit request =>
    Ok("Good!")
  }

  def update(id: BSONObjectID) = userAction { implicit request =>
    Ok("!")
  }

}
