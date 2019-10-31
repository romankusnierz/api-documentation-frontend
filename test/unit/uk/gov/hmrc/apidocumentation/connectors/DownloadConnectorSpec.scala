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

package unit.uk.gov.hmrc.apidocumentation.connectors

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.Matchers.{any, anyString, eq => eqTo}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import play.api.http.Status._
import play.api.libs.ws.{DefaultWSResponseHeaders, StreamedResponse}
import uk.gov.hmrc.apidocumentation.config.ApplicationConfig
import uk.gov.hmrc.apidocumentation.connectors.DownloadConnector
import uk.gov.hmrc.apidocumentation.services.ProxyAwareApiDefinitionService
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

class DownloadConnectorSpec extends ConnectorSpec with MockitoSugar {

  val serviceName = "hello-world"
  val version = "1.0"

// TODO - remove when we are sure these are covered
//  val mockWS = MockWS {
//    case ("GET", `resourceFoundUrl`) => Action(Results.Ok("hello world"))
//    case ("GET", `resourceNotFoundUrl`) => Action(Results.NotFound)
//    case ("GET", `serviceUnavailableUrl`) => Action(Results.ServiceUnavailable)
//    case ("GET", `timeoutUrl`) => Action(Results.RequestTimeout)
//  }

  trait Setup {
    implicit val hc = HeaderCarrier()
    val mockAppConfig = mock[ApplicationConfig]
    val mockApiDefinitionService = mock[ProxyAwareApiDefinitionService]

    def streamedResponse(str: String) = StreamedResponse(
        DefaultWSResponseHeaders(OK, Map("Content-length" -> Seq(s"${str.length()}"))),
        Source(List(ByteString(str)))
    )

    val neverCompletes = {
      val p = Promise[StreamedResponse]()
      p.future
    }

    when(mockApiDefinitionService.fetchApiDocumentationResource(anyString(), anyString(), eqTo("some/resource"))(any[HeaderCarrier])).thenReturn(Future.successful(Some(streamedResponse("Hello world"))))
    when(mockApiDefinitionService.fetchApiDocumentationResource(anyString(), anyString(), eqTo("some/resourceNotThere"))(any[HeaderCarrier])).thenReturn(Future.failed(new NotFoundException("bang")))
    when(mockApiDefinitionService.fetchApiDocumentationResource(anyString(), anyString(), eqTo("some/resourceInvalid"))(any[HeaderCarrier])).thenReturn(Future.failed(new InternalServerException("bang")))
    val connector = new DownloadConnector(mockApiDefinitionService, mockAppConfig)
  }

  "downloadResource" should {
    "return resource when found" in new Setup {

      val result = await(connector.fetch(serviceName, version, "some/resource"))
      result.header.status shouldBe OK
    }

    "throw NotFoundException when not found" in new Setup {
      intercept[NotFoundException] {
        await(connector.fetch(serviceName, version, "some/resourceNotThere"))
      }
    }

    "throw InternalServerException for any other response" in new Setup {
      intercept[InternalServerException] {
        await(connector.fetch(serviceName, version, "some/resourceInvalid"))
      }
    }
  }

}
