package models

import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.sql.Timestamp
import slick.driver.PostgresDriver.api._

import scala.concurrent.{ Future, ExecutionContext }

@Singleton
class UserRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._    // Bring db in scope
  import profile.api._ // Slick DSL

  // Define table
  private class UserTable(tag: Tag) extends Table[User](tag, "users") {

    // Define columns
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def email = column[String]("email")
    def passwordHash = column[String]("password_hash")
    def createdAt = column[Timestamp]("created_at")
    def updatedAt = column[Timestamp]("updated_at")

    // Default Projection
    def * = (id, name, email, passwordHash, createdAt, updatedAt) <> (User.tupled, User.unapply)
  }

  private val users = TableQuery[UserTable]

  // =================
  // Define CRUD here.
  // =================

  // List all users.
  def list(): Future[Seq[User]] = db.run {
    users.result
  }

  def create(name: String, email: String, password: String): Future[User] = db.run {
    (users.map(u => (u.name, u.email, u.passwordHash))
      returning users.map(u => (u.id, u.createdAt, u.updatedAt))
      into ((form, user) => User(user._1, form._1, form._2, form._3, user._2, user._3))
      ) += (name, email, password)
  }
}


