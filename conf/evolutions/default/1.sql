# --- !Ups
CREATE TABLE "users" (
  "id" bigserial primary key,
  "name" character varying,
  "email" character varying,
  "password_hash" character varying,
  "created_at" timestamp NOT NULL default now(),
  "updated_at" timestamp NOT NULL default now()
)

# --- !Downs

DROP TABLE IF EXISTS "users"
