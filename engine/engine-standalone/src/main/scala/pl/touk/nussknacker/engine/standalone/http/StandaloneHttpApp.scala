package pl.touk.nussknacker.engine.standalone.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.standalone.management.DeploymentService
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer

import scala.util.Try

object StandaloneHttpApp extends Directives with Argonaut62Support with LazyLogging with App {

  implicit val system = ActorSystem("nussknacker-standalone-http")

  import system.dispatcher

  implicit private val materializer = ActorMaterializer()

  val standaloneApp = new StandaloneHttpApp(ConfigFactory.load())

  val managementPort = Try(args(0).toInt).getOrElse(8070)
  val processesPort = Try(args(1).toInt).getOrElse(8080)

  Http().bindAndHandle(
    standaloneApp.managementRoute.route,
    interface = "0.0.0.0",
    port = managementPort
  )

  Http().bindAndHandle(
    standaloneApp.processRoute.route,
    interface = "0.0.0.0",
    port = processesPort
  )

}

class StandaloneHttpApp(config: Config)(implicit as: ActorSystem)
  extends Directives with Argonaut62Support with LazyLogging {

  private val deploymentService = DeploymentService(prepareContext(), config)

  val managementRoute = new ManagementRoute(deploymentService)

  val processRoute = new ProcessRoute(deploymentService)

  private def prepareContext(): StandaloneContextPreparer = {
    val metricRegistry = new MetricRegistry
    GraphiteReporter.forRegistry(metricRegistry)
      .prefixedWith(s"standaloneEngine.${config.getString("hostName")}")
        .build(new Graphite(config.getString("graphite.hostName"), config.getInt("graphite.port")))
    new StandaloneContextPreparer(metricRegistry)
  }
}