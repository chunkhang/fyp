# FYP

> Final Year Project

![picture1](https://user-images.githubusercontent.com/12708862/47428353-9699ef80-d7c5-11e8-963a-7d9a95313faa.png)
![picture2](https://user-images.githubusercontent.com/12708862/47428354-9699ef80-d7c5-11e8-9856-95e36d73bd0f.png)
![picture3](https://user-images.githubusercontent.com/12708862/47428356-97328600-d7c5-11e8-9798-d39d0cf55353.png)
![picture4](https://user-images.githubusercontent.com/12708862/47428357-97cb1c80-d7c5-11e8-867e-822506644f8b.png)

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
