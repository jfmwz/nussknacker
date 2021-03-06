package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.process.uiconfig.SingleNodeConfig
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class SettingsResources(config: FeatureTogglesConfig, nodesConfig: Map[String, SingleNodeConfig])(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with RouteWithUser {

  import argonaut.ArgonautShapeless._

  def route(implicit user: LoggedUser): Route =
    pathPrefix("settings") {
      get {
        complete {
          val toggleOptions = ToggleFeaturesOptions(
            counts = config.counts.isDefined,
            search = config.search,
            metrics = config.metrics,
            remoteEnvironment = config.remoteEnvironment.map(c => RemoteEnvironmentConfig(c.environmentId)),
            environmentAlert = config.environmentAlert
          )
          UISettings(toggleOptions, nodesConfig)
        }
      }
    }
}

case class GrafanaSettings(url: String, defaultDashboard: String, processingTypeToDashboard: Option[Map[String,String]], env: String)
case class KibanaSettings(url: String)
case class RemoteEnvironmentConfig(targetEnvironmentId: String)
case class EnvironmentAlert(content: String, cssClass: String)

case class ToggleFeaturesOptions(counts: Boolean,
                                 search: Option[KibanaSettings],
                                 metrics: Option[GrafanaSettings],
                                 remoteEnvironment: Option[RemoteEnvironmentConfig],
                                 environmentAlert: Option[EnvironmentAlert]
                                )

case class UISettings(features: ToggleFeaturesOptions, nodes: Map[String, SingleNodeConfig])