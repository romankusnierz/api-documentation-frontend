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

package uk.gov.hmrc.apidocumentation.models

trait ExtendedApiDefinitionCombiner {
  def combineExtendedApiDefinitions(maybePrincipalDefinition: Option[ExtendedAPIDefinition], maybeSubordinateDefinition: Option[ExtendedAPIDefinition]): Option[ExtendedAPIDefinition] = {
    def findProductionDefinition(maybePrincipalDefinition: Option[ExtendedAPIDefinition], maybeSubordinateDefinition: Option[ExtendedAPIDefinition]) = {
      if (maybePrincipalDefinition.exists(_.versions.exists(_.productionAvailability.isDefined))) {
        maybePrincipalDefinition
      } else {
        maybeSubordinateDefinition
      }
    }

    def findSandboxDefinition(maybePrincipalDefinition: Option[ExtendedAPIDefinition], maybeSubordinateDefinition: Option[ExtendedAPIDefinition]) = {
      if (maybePrincipalDefinition.exists(_.versions.exists(_.sandboxAvailability.isDefined))) {
        maybePrincipalDefinition
      } else {
        maybeSubordinateDefinition
      }
    }

    def combineVersion(maybePrincipalVersion: Option[ExtendedAPIVersion], maybeSubordinateVersion: Option[ExtendedAPIVersion]) = {
      maybePrincipalVersion.fold(maybeSubordinateVersion) { productionVersion =>
        maybeSubordinateVersion.fold(maybePrincipalVersion) { sandboxVersion =>
          Some(sandboxVersion.copy(productionAvailability = productionVersion.productionAvailability))
        }
      }
    }

    def combineVersions(principalVersions: Seq[ExtendedAPIVersion], subordinateVersions: Seq[ExtendedAPIVersion]): Seq[ExtendedAPIVersion] = {
      val allVersions = (principalVersions.map(_.version) ++ subordinateVersions.map(_.version)).distinct.sorted
      allVersions.flatMap { version =>
        combineVersion(principalVersions.find(_.version == version), subordinateVersions.find(_.version == version))
      }
    }


    val maybeProductionDefinition = findProductionDefinition(maybePrincipalDefinition, maybeSubordinateDefinition)
    val maybeSandboxDefinition = findSandboxDefinition(maybePrincipalDefinition, maybeSubordinateDefinition)

    maybeProductionDefinition.fold(maybeSandboxDefinition) { productionDefinition =>
      maybeSandboxDefinition.fold(maybeProductionDefinition) { sandboxDefinition =>
        Some(sandboxDefinition.copy(versions = combineVersions(productionDefinition.versions, sandboxDefinition.versions)))
      }
    }
  }
}
