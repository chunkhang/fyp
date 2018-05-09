package models

import java.sql.Timestamp

case class User(id: Long, name: String, email: String, passwordHash: String,createdAt: Timestamp, updatedAt: Timestamp)
