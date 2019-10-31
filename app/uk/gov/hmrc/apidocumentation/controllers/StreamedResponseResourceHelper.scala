package uk.gov.hmrc.apidocumentation.controllers

import scala.concurrent.Future
import uk.gov.hmrc.http.NotFoundException

trait StreamedResponseResourceHelper extends StreamedResponseHelper {

  def handler(serviceName: String, version: String, resource: String) = streamedResponseAsResult(
    handleNotFoundResponse(s"$resource not found for $serviceName $version")
      orElse handleErrorsAsInternalServerError(s"Error downloading $resource for $serviceName $version")
  )(_)

  def failedDueToNotFoundException(serviceName: String, version: String, resource: String): Future[Nothing] = {
    Future.failed(new NotFoundException(s"$resource not found for $serviceName $version"))
  }
}
