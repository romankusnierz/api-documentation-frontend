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

package uk.gov.hmrc.apidocumentation.controllers

import play.api.http.Status.NOT_FOUND
import play.api.mvc.Result
import play.api.http.{HttpEntity, Status}
import play.api.libs.ws.StreamedResponse
import play.api.mvc.Results._
import uk.gov.hmrc.http.{InternalServerException, NotFoundException}

trait StreamedResponseHelper {
  type StreamedResponseHandlerPF = PartialFunction[StreamedResponse, Result]
  def handleOkStreamedResponse: StreamedResponseHandlerPF = {
    case response: StreamedResponse if response.headers.status == Status.OK =>
      val wsHeaders = response.headers

      // Get the content type
      val contentType = wsHeaders.headers
        .get("Content-Type")
        .flatMap(_.headOption)
        .getOrElse("application/octet-stream")

      // If there's a content length, send that, otherwise return the body chunked
      wsHeaders.headers.get("Content-Length") match {
        case Some(Seq(length)) =>
          Ok.sendEntity(HttpEntity.Streamed(response.body, Some(length.toLong), Some(contentType)))
        case _ =>
          Ok.chunked(response.body).as(contentType)
      }
  }

  def handleNotFoundResponse(msg: String): StreamedResponseHandlerPF = {
    case response if response.headers.status == NOT_FOUND =>
      throw new NotFoundException(msg)
  }

  def handleErrorsAsInternalServerError(msg: String): StreamedResponseHandlerPF = {
    case _ =>
      throw new InternalServerException(msg)
  }

  def streamedResponseAsResult(handleError: StreamedResponseHandlerPF)(streamedResponse: StreamedResponse): Result = {
    val fn = handleOkStreamedResponse orElse handleError

    if( fn.isDefinedAt(streamedResponse)) {
      fn(streamedResponse)
    } else {
      BadGateway
    }
  }
}

object StreamedResponseHelper extends StreamedResponseHelper
