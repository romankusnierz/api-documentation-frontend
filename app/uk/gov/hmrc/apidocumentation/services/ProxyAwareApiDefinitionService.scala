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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apidocumentation.models.{APIDefinition, ApiDefinitionCombiner, ExtendedAPIDefinition, ExtendedAPIVersion, ExtendedApiDefinitionCombiner}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProxyAwareApiDefinitionService @Inject()(principal: PrincipalApiDefinitionService,
                                               subordinate: SubordinateApiDefinitionService
                                              )(implicit val ec: ExecutionContext)
  extends BaseApiDefinitionService
    with ApiDefinitionCombiner
    with ExtendedApiDefinitionCombiner{

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
    } yield combineExtendedApiDefinitions(maybePrincipalDefinition, maybeSubordinateDefinition)
  }
}
