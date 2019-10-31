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

import javax.inject.{Inject, Singleton}
import play.api.libs.ws._
import play.api.mvc._
import uk.gov.hmrc.apidocumentation.config.ApplicationConfig
import uk.gov.hmrc.apidocumentation.controllers.{StreamedResponseHelper, StreamedResponseResourceHelper}
import uk.gov.hmrc.apidocumentation.services.ProxyAwareApiDefinitionService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO - Remove and replace with direct ApiDefnConnector or further up stack
@Singleton
class DownloadConnector @Inject()(
                                   apiDefinitionService: ProxyAwareApiDefinitionService,
                                   appConfig: ApplicationConfig
                                 )
  extends StreamedResponseResourceHelper {

  private def makeRequest(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[StreamedResponse] = {
    apiDefinitionService.fetchApiDocumentationResource(serviceName,version,resource).map(_.get)
  }

  def fetch(serviceName: String, version: String, resource: String)(implicit hc:HeaderCarrier): Future[Result] = {
    makeRequest(serviceName, version, resource).map( handler(serviceName,version,resource) )
  }
}

