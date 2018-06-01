import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws._
import play.api.{Logger, Configuration}
import com.google.inject.AbstractModule
import models._

// Initial code to run during start up
class StartUpService @Inject()(
  ws: WSClient,
  config: Configuration,
  venueRepo: VenueRepository
)(
  implicit ec: ExecutionContext
) {

  // Populate venue collection if empty
  venueRepo.readAll().map { venues =>
    if (venues.isEmpty) {
      // GET request to get all venues
      ws.url(config.get[String]("my.api.icheckin.venueUrl"))
        .get().map { response =>
        val names = (response.json \\ "name").map { name =>
          name.as[String]
        }
        val buildings = (response.json \\ "building").map { building =>
          building.as[String]
        }
        val venues = names zip buildings
        // Create venues in database
        Future.traverse(venues) { venue =>
          val (name_, building_) = venue
          venueRepo.create(Venue(
            name = name_,
            building = building_
          ))
        }.map { _ =>
          Logger.info("Populated venues collection")
        }
      }
    }
  }

}

class Module extends AbstractModule {

    override def configure() = {
        bind(classOf[StartUpService]).asEagerSingleton
    }

}
