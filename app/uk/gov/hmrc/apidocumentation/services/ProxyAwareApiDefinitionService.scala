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

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.data.OptionT
import javax.inject.{Inject, Singleton}
import play.api.libs.ws.StreamedResponse
import uk.gov.hmrc.apidocumentation.config.ApplicationConfig
import uk.gov.hmrc.apidocumentation.models.{APIDefinition, ApiDefinitionCombiner, ExtendedAPIDefinition, ExtendedAPIVersion, ExtendedApiDefinitionCombiner}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProxyAwareApiDefinitionService @Inject()(principal: PrincipalApiDefinitionService,
                                               subordinate: SubordinateApiDefinitionService,
                                               appConfig: ApplicationConfig
                                              )
                                              (implicit val ec: ExecutionContext,
                                               val actorSystem: ActorSystem,
                                               val mat: Materializer
                                              )
                                              extends BaseApiDefinitionService
                                                with ExtendedApiDefinitionCombiner
                                                with ApiDefinitionCombiner {

  private def newNotFoundException(serviceName: String, version: String, resource: String) = {
    new NotFoundException(s"$resource not found for $serviceName $version")
  }

  def fetchAllDefinitions(thirdPartyDeveloperEmail: Option[String])
                         (implicit hc: HeaderCarrier): Future[Seq[APIDefinition]] = {

    val principalFuture = principal.fetchAllDefinitions(thirdPartyDeveloperEmail)
    val subordinateFuture = subordinate.fetchAllDefinitions(thirdPartyDeveloperEmail)

    for {
      subordinateDefinitions <- subordinateFuture
      principalDefinitions <- principalFuture
    } yield combineDefintions(principalDefinitions, subordinateDefinitions)
  }

  def fetchExtendedDefinition(serviceName: String, thirdPartyDeveloperEmail: Option[String])
                        (implicit hc: HeaderCarrier): Future[Option[ExtendedAPIDefinition]] = {
    val principalFuture = principal.fetchExtendedDefinition(serviceName, thirdPartyDeveloperEmail)
    val subordinateFuture = subordinate.fetchExtendedDefinition(serviceName, thirdPartyDeveloperEmail)

    for {
      maybePrincipalDefinition <- principalFuture
      maybeSubordinateDefinition <- subordinateFuture
      combined = combineExtendedApiDefinitions(maybePrincipalDefinition, maybeSubordinateDefinition)
    } yield combined.filterNot(_.requiresTrust)
  }

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[Option[StreamedResponse]] = {
    import cats.implicits._

    def fetchApiVersion: Future[ExtendedAPIVersion] = {
      val error = Future.failed[ExtendedAPIVersion](new IllegalArgumentException(s"Version $version of $serviceName not found"))

      fetchExtendedDefinition(serviceName, None)
        .map(_.flatMap(_.versions.find(_.version == version)))
        .flatMap(_.fold(error)(v => Future.successful(v)))
    }

    def fetchSubordinateOrPrincipal(serviceName: String, version: String, resource: String) = {
      val subordinateData = OptionT(subordinate.fetchApiDocumentationResource(serviceName, version, resource))
      lazy val principalData = OptionT(principal.fetchApiDocumentationResource(serviceName, version, resource))

      subordinateData
        .orElse(principalData)
        .getOrElseF(Future.failed(newNotFoundException(serviceName, version, resource)))
    }

    def fetchPrincipalResourceOnly(serviceName: String, version: String, resource: String) = {
      OptionT(principal.fetchApiDocumentationResource(serviceName, version, resource))
        .getOrElseF(Future.failed(newNotFoundException(serviceName, version, resource)))
    }

    def fetchResource(isAvailableInSandbox: Boolean): Future[StreamedResponse] = {
      // TODO : Does the none from subordinate negate the need for this ?
      if(isAvailableInSandbox) {
        fetchSubordinateOrPrincipal(serviceName, version, resource)
      } else {
        fetchPrincipalResourceOnly(serviceName, version, resource)
      }
    }

    for {
      apiVersion <- fetchApiVersion
      response <- fetchResource(apiVersion.sandboxAvailability.isDefined)
    } yield response.some
  }
}
