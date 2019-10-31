package uk.gov.hmrc.apidocumentation.controllers

import uk.gov.hmrc.apidocumentation.models.{ExtendedAPIDefinition, ExtendedAPIVersion}

trait ApiVersionVisibilityHelper {

  def findVersion(apiOption: Option[ExtendedAPIDefinition], version: String) =
    for {
      api <- apiOption
      apiVersion <- api.versions.find(v => v.version == version)
      visibility <- apiVersion.visibility
    } yield (api, apiVersion, visibility)
}
