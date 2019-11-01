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
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import cats.data.OptionT
import com.google.inject.{Inject, Singleton}
import play.api.http.Status
import play.api.libs.ws.{DefaultWSResponseHeaders, StreamedResponse, WSResponseHeaders}
import uk.gov.hmrc.apidocumentation.controllers.StreamedResponseResourceHelper
import uk.gov.hmrc.apidocumentation.models.{APIDefinition, ExtendedAPIDefinition}
import uk.gov.hmrc.apidocumentation.repositories.{ResourceData, ResourceRepository}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StoredResourceApiDefinitionService @ Inject()(
                                          inner: ProxyAwareApiDefinitionService,
                                          resourceRepository: ResourceRepository
                                        )(
                                          implicit val ec: ExecutionContext,
                                          val mat: Materializer
                                        )
    extends BaseApiDefinitionService
      with StreamedResponseResourceHelper {

  override def fetchExtendedDefinition(serviceName: String, email: Option[String])(implicit hc: HeaderCarrier): Future[Option[ExtendedAPIDefinition]] =
    inner.fetchExtendedDefinition(serviceName,email)

  override def fetchAllDefinitions(email: Option[String])(implicit hc: HeaderCarrier): Future[Seq[APIDefinition]] =
    inner.fetchAllDefinitions(email)

  override def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[Option[StreamedResponse]] = {
    import cats.implicits._

    def asStreamedResponse(resourceData: ResourceData): StreamedResponse = {
      val source = Source.single(ByteString(resourceData.contents))
      val headers = Map("Content-Length" -> Seq(resourceData.contents.length.toString))
      StreamedResponse(DefaultWSResponseHeaders(Status.OK, headers), source)
    }

    val storedData = OptionT(resourceRepository.fetch(serviceName, version, resource)).map(asStreamedResponse)

    storedData
      .orElse(fetchThenStore(serviceName,version,resource))
      .getOrElseF(failedDueToNotFoundException(serviceName, version, resource))
      .map(Some(_))
  }

  private def fetchThenStore(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): OptionT[Future,StreamedResponse] = {
    def asArrayOfBytes(source: Source[ByteString, _]): Future[Array[Byte]] =
      source
        .runWith(Sink.reduce[ByteString](_ ++ _))
        .map { r: ByteString => r.toArray[Byte] }

    def asStreamedResponse(headers: WSResponseHeaders, content: Array[Byte]): StreamedResponse = {
      val source = Source.single(ByteString(content))
      StreamedResponse(headers, source)
    }

    import cats.implicits._

    def extractContentsAndStore(streamedResponse: StreamedResponse): Future[StreamedResponse] = streamedResponse match {
      case StreamedResponse(wsHeaders, body) if(wsHeaders.status == Status.OK) =>
        for {
               content <- asArrayOfBytes(body)
                 _ <- resourceRepository.save(ResourceData(serviceName, version, resource, content))
        } yield asStreamedResponse(wsHeaders, content) // Create new streamedResponse to cater for read-once streams

      case response: StreamedResponse => response.pure[Future]
    }

    OptionT(inner.fetchApiDocumentationResource(serviceName, version, resource))
      .semiflatMap(extractContentsAndStore)
  }
}
