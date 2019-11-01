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

package uk.gov.hmrc.apidocumentation.services

import javax.inject.Inject
import org.raml.v2.api.model.v10.resources.Resource
import play.api.Logger
import play.api.cache._
import uk.gov.hmrc.apidocumentation.config.ApplicationConfig
import uk.gov.hmrc.apidocumentation.models.{RamlAndSchemas, TestEndpoint}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ramltools.loaders.RamlLoader

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object DocumentationService {
  def ramlUrl(serviceName: String, version: String): String =
    //TODO - make url using absolute url so it works in QA etc.
    "http://localhost:9680" + uk.gov.hmrc.apidocumentation.controllers.routes.ProxyRamlController.downloadRaml(serviceName,version).url

  def schemasBaseUrl(serviceName: String, version: String): String = {
    val url = ramlUrl(serviceName, version)
    schemasBaseUrl(url)
  }

  def schemasBaseUrl(ramlUrl: String): String = {
    s"${ramlUrl.take(ramlUrl.lastIndexOf('/'))}/schemas"
  }
}

// TODO : Is this just raml and schema service ??
class DocumentationService @Inject()(appConfig: ApplicationConfig,
                                     cache: CacheApi,
                                     ramlLoader: RamlLoader,
                                     schemaService: SchemaService) {

  import DocumentationService._

  val defaultExpiration = 1.hour

  def fetchRAML(serviceName: String, version: String, cacheBuster: Boolean)(implicit hc: HeaderCarrier): Future[RamlAndSchemas] = {
      val url = ramlUrl(serviceName,version)
      fetchRAML(url, cacheBuster)
  }

  def fetchRAML(url: String, cacheBuster: Boolean): Future[RamlAndSchemas] = {
    Logger.info(s"@Pomegranate fetchRaml using $url")
    if (cacheBuster) cache.remove(url)

    Future {
      blocking {
        Logger.info(s"@Pomegranate in future")
        cache.getOrElse[Try[RamlAndSchemas]](url, defaultExpiration) {
        ramlLoader.load(url)
          .map(raml => {
          val schemaBasePath =  schemasBaseUrl(url)
            RamlAndSchemas(raml, schemaService.loadSchemas(schemaBasePath, raml))
          })
        }
      }
    } flatMap {
      case Success(api) => Future.successful(api)
      case Failure(e) =>
        cache.remove(url)
        Future.failed(e)
    }
  }

  def buildTestEndpoints(service: String, version: String)(implicit hc: HeaderCarrier): Future[Seq[TestEndpoint]] = {
    fetchRAML(service, version, cacheBuster = true).map { ramlAndSchemas =>
      buildResources(ramlAndSchemas.raml.resources.toSeq)
    }
  }

  private def buildResources(resources: Seq[Resource]): Seq[TestEndpoint] = {
    resources.flatMap { res =>
      val nested = buildResources(res.resources())
      res.methods.headOption match {
        case Some(_) =>
          val methods = res.methods.map(_.method.toUpperCase).sorted
          val endpoint = TestEndpoint(s"{service-url}${res.resourcePath}", methods:_*)

          endpoint +: nested

        case _ => nested
      }
    }
  }
}
