package controllers

import javax.inject.Inject

import models.JsonFormats._
import models.{Todo, TodoRepository}
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by Riccardo Sirigu on 10/08/2017.
  */
class TodoController @Inject()(cc: ControllerComponents, todoRepo: TodoRepository) extends AbstractController(cc) {

  def getAllTodos = Action.async {
    todoRepo.getAll.map{ todos =>
      Ok(Json.toJson(todos))
    }
  }

  def getTodo(todoId: BSONObjectID) = Action.async{ req =>
    todoRepo.getTodo(todoId).map{ maybeTodo =>
      maybeTodo.map{ todo =>
        Ok(Json.toJson(todo))
      }.getOrElse(NotFound)
    }
  }

  def createTodo() = Action.async(parse.json){ req =>
    req.body.validate[Todo].map{ todo =>
      todoRepo.addTodo(todo).map{ _ =>
        Created
      }
    }.getOrElse(Future.successful(BadRequest("Invalid Todo format")))
  }

  def updateTodo(todoId: BSONObjectID) = Action.async(parse.json){ req =>
    req.body.validate[Todo].map{ todo =>
      todoRepo.updateTodo(todoId, todo).map {
        case Some(todo) => Ok(Json.toJson(todo))
        case None => NotFound
      }
    }.getOrElse(Future.successful(BadRequest("Invalid Json")))
  }

  def deleteTodo(todoId: BSONObjectID) = Action.async{ req =>
    todoRepo.deleteTodo(todoId).map {
      case Some(todo) => Ok(Json.toJson(todo))
      case None => NotFound
    }
  }

}
