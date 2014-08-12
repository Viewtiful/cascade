/**
 * Copyright 2013-2014 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.paypal.stingray.http.resource

import spray.can.Http.GetStats
import spray.can.server.Stats
import spray.http._
import spray.http.ContentTypes
import spray.http.HttpEntity
import spray.http.StatusCodes._
import spray.routing._
import com.paypal.stingray.akka.actor.ActorSystemComponent
import com.paypal.stingray.common.service.ServiceNameComponent
import com.paypal.stingray.common.properties.BuildProperties
import com.paypal.stingray.json._
import com.paypal.stingray.http.server.StatusResponse
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import akka.actor.ActorSelection
import akka.pattern.ask

/**
 * Base type for implementing resource-based services. Contains several often-used patterns, e.g. stats and status
 * endpoints, and is driven by [[com.paypal.stingray.http.resource.ResourceDriver]].
 *
 * Implementing classes should override `route` with their own Spray route logic. This should serve as the top-most
 * actor (or very close to top) in a Spray-based actor hierarchy, as it is the first point of entry into most services.
 *
 * See https://confluence.paypal.com/cnfl/display/stingray/AbstractResource%2C+ResourceDriver%2C+and+ResourceService
 * for more information.
 */
trait ResourceServiceComponent {
    this: ServiceNameComponent
    with ActorSystemComponent =>

  /**
   * Configuration value provided
   * The routing rules for this service
   */
  val route: Route

  /**
   * Interface provided
   * This will be extended by the SprayActor
   */
  trait ResourceService
    extends HttpService {

    /** A source of build-specific values for this service */
    protected lazy val buildProps = new BuildProperties

    private lazy val statusResponse = StatusResponse.getStatusResponse(buildProps, serviceName)

    private lazy val statusError = """{"status":"error"}"""

    private lazy val statusRoute: Route = (path("status") & headerValueByName("x-service-status")) { _ =>
      (ctx: RequestContext) => {
        val statusRespJson = JsonUtil.toJson(statusResponse).getOrElse(statusError)
        ctx.complete(HttpResponse(OK, HttpEntity(ContentTypes.`application/json`, statusRespJson)))
      }
    }

    private lazy val serverActor: ActorSelection = actorRefFactory.actorSelection("/user/IO-HTTP/listener-0")

    private lazy val statsError = """{"stats":"error"}"""

    private lazy val statsRoute: Route = (path("stats") & headerValueByName("x-service-stats")) { _ =>
      (ctx: RequestContext) => {
        (serverActor ? GetStats)(1.second).mapTo[Stats].onComplete {
          case Success(stats) => {
            val statsRespJson = JsonUtil.toJson(stats).getOrElse(statsError)
            ctx.complete(HttpResponse(OK, HttpEntity(ContentTypes.`application/json`, statsRespJson)))
          }
          case Failure(t) => {
            val statsFailureJson =
              JsonUtil.toJson(Map("errors" -> List(Option(t.getMessage).getOrElse("")))).getOrElse(statsError)
            ctx.complete(
              HttpResponse(InternalServerError, HttpEntity(ContentTypes.`application/json`, statsFailureJson)))
          }
        }
      }
    }

    /** The route before sealing into `fullRoute`. This should not be overridden. */
    protected lazy val unsealedFullRoute: Route = statusRoute ~ statsRoute ~ route

    /** The route after sealing, which will be used to handle requests. This should not be overridden.*/
    lazy val fullRoute: Route = sealRoute(unsealedFullRoute)

  }

}
