# FYP

> Final Year Project

## Dependencies

- [SBT](https://www.scala-sbt.org/): Dependency Management
- [MongoDB](https://www.mongodb.com/): Database

## Environment

### conf/environment.conf
```
environment {
  applicationSecret = ""
  clientId = ""
  clientSecret = ""
}
```

## Running

- Make sure MongoDB is running: `brew services start mongodb`
- Start SBT: `sbt`
- Run server in SBT: `run`
- Open browser: `open http://localhost:9000/`

