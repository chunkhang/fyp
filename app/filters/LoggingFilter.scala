package filters

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger
import play.api.mvc._
import akka.stream.Materializer

class LoggingFilter @Inject()(
  implicit val mat: Materializer,
  ec: ExecutionContext
) extends Filter {

  def apply(
    nextFilter: RequestHeader => Future[Result]
  )(
    requestHeader: RequestHeader
  ): Future[Result] = {
    val startTime = System.currentTimeMillis
    nextFilter(requestHeader).map { result =>
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime
      if (result.header.status != 304) {
        Logger.info(
          s"${requestHeader.method} ${requestHeader.uri} " +
          s"[${result.header.status}] (${requestTime}ms)"
        )
      }
      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }

}
