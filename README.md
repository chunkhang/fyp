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
  emailUser = ""
  emailPassword = ""
}
```

## Running

- Make sure [FYP API](https://github.com/chunkhang/fyp-api) is running: `open http://localhost:5000/`
- Make sure MongoDB is running: `brew services start mongodb`
- Start SBT: `sbt`
- Run server in SBT: `run`
- Open browser: `open http://localhost:9000/`
