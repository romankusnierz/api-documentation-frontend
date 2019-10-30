/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apidocumentation.connectors

import akka.actor.ActorSystem
import akka.pattern.FutureTimeoutSupport
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.ws.StreamedResponse
import uk.gov.hmrc.apidocumentation.config.ApplicationConfig
import uk.gov.hmrc.apidocumentation.connectors.ApiDefinitionConnector._
import uk.gov.hmrc.apidocumentation.models.{APIDefinition, ExtendedAPIDefinition}
import uk.gov.hmrc.apidocumentation.models.JsonFormatters._
import uk.gov.hmrc.apidocumentation.utils.Retries
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.ws.WSGet

import scala.concurrent.{ExecutionContext, Future}

trait ApiDefinitionConnector {
  def http: HttpClient with WSGet
  def serviceBaseUrl: String
  implicit val ec: ExecutionContext

  def fetchAllApiDefinitions(email: Option[String])(implicit hc: HeaderCarrier): Future[Seq[APIDefinition]] = {
    Logger.info(s"${this.getClass.getSimpleName} - fetchAllApiDefinitions")
    val r= http.GET[Seq[APIDefinition]](definitionsUrl(serviceBaseUrl), queryParams(email))

    r.foreach(_.foreach(defn => Logger.info(s"Found ${defn.name}")))

    r.map(e => e.sortBy(
      _.name
    ))
    .recover {
      case _ : NotFoundException => { Logger.info("Not found"); Seq.empty}
      case e => { Logger.error(s"Failed ${e}"); throw e}
    }
  }

  def fetchApiDefinition(serviceName: String, email: Option[String])(implicit hc: HeaderCarrier): Future[Option[ExtendedAPIDefinition]] = {
    Logger.info(s"${this.getClass.getSimpleName} - fetchApiDefinition")
    val r = http.GET[ExtendedAPIDefinition](definitionUrl(serviceBaseUrl,serviceName), queryParams(email))

    r.foreach(defn => Logger.info(s"Found ${defn.name}"))

    r.map(Some(_))
      .recover {
        case _ : NotFoundException => { Logger.info("Not found"); None}
        case e => { Logger.error(s"Failed ${e}"); throw e}
      }
  }

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[Option[StreamedResponse]]

}

object ApiDefinitionConnector {
  type Params = Seq[(String, String)]

  val noParams: Params = Seq.empty

  def queryParams(oemail: Option[String]): Params =
    oemail.fold(noParams)(email => Seq("email" -> email))

  def definitionsUrl(serviceBaseUrl: String) = s"$serviceBaseUrl/api-definition"

  def definitionUrl(serviceBaseUrl: String, serviceName: String) = s"$serviceBaseUrl/api-definition/$serviceName/extended"

  def documentationUrl(serviceBaseUrl: String, serviceName: String, version: String, resource: String) =
    s"$serviceBaseUrl/api-definition/$serviceName/$version/documentation/$resource"
}

@Singleton
class PrincipalApiDefinitionConnector @Inject()(
      val http: HttpClient with WSGet,
      val appConfig: ApplicationConfig
    )
    (implicit val ec: ExecutionContext) extends ApiDefinitionConnector {

  val serviceBaseUrl = appConfig.apiDefinitionPrincipalBaseUrl

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[Option[StreamedResponse]] = {
    Logger.info(s"${this.getClass.getSimpleName} - fetchApiDocumentationResource")

    http
      .buildRequest(documentationUrl(serviceBaseUrl, serviceName, version, resource))
      .stream()
      .map(Some(_))
  }
}

@Singleton
class SubordinateApiDefinitionConnector @Inject()(
    val appConfig: ApplicationConfig,
    val httpClient: HttpClient with WSGet,
    val proxiedHttpClient: ProxiedHttpClient,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
    )
                                                 (implicit val ec: ExecutionContext)
    extends ApiDefinitionConnector with Retries {

  val serviceBaseUrl = appConfig.apiDefinitionSubordinateBaseUrl

  val useProxy = appConfig.apiDefinitionSubordinateUseProxy
  val bearerToken = appConfig.apiDefinitionSubordinateBearerToken
  val apiKey = appConfig.apiDefinitionSubordinateApiKey

  override def http: HttpClient with WSGet =
    if (useProxy)
      proxiedHttpClient.withHeaders(bearerToken, apiKey)
    else
      httpClient

  override def fetchAllApiDefinitions(email: Option[String] = None)
                                     (implicit hc: HeaderCarrier): Future[Seq[APIDefinition]] = {
    retry {
      super.fetchAllApiDefinitions(email)
    }
  }

  override def fetchApiDefinition(serviceName: String, email: Option[String] = None)
                                 (implicit hc: HeaderCarrier): Future[Option[ExtendedAPIDefinition]] = {
    retry {
      super.fetchApiDefinition(serviceName, email)
    }
  }

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[Option[StreamedResponse]] = {
    Logger.info(s"${this.getClass.getSimpleName} - fetchApiDocumentationResource")

    if (useProxy) {
      retry {
        proxiedHttpClient
          .withHeaders(bearerToken, apiKey)
          .buildRequest(documentationUrl(serviceBaseUrl, serviceName, version, resource)).stream()
          .map(Some(_))
      }
    } else {
      Future.successful(None)
    }
  }
}
