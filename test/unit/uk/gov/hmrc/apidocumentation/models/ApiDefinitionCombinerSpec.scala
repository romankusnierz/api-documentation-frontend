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

package unit.uk.gov.hmrc.apidocumentation.models

import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidocumentation.models.{ApiDefinitionCombiner, _}
import uk.gov.hmrc.play.test.UnitSpec
import unit.uk.gov.hmrc.apidocumentation.utils.{ApiDefinitionTestDataHelper, BaseApiDefinitionServiceMockingHelper}

object ApiDefinitionCombinerSpec {
  implicit class SeqOps[A](val item: A) {
    def asSeq: Seq[A] = Seq(item)
  }
}

class ApiDefinitionCombinerSpec
  extends UnitSpec
    with MockitoSugar
    with ApiDefinitionTestDataHelper
    with BaseApiDefinitionServiceMockingHelper {

  val serviceName = "api-example-microservice"

  trait Setup {
    val underTest = new ApiDefinitionCombiner {}

    val apiVersion1 = apiVersion("1.0")
    val apiVersion2 = apiVersion("2.0")
  }

  def testDefn(serviceName: String, versions: APIVersion*) = {
    APIDefinition(serviceName, "Hello World", "Example", "hello", None, None, versions.toSeq)
  }

  "apiRequiresTrust" should {
    val apiVersion1 = APIVersion("1.0", None, APIStatus.STABLE, Seq.empty)
    val data = testDefn(serviceName, apiVersion1)

    "return true when the api requires trust" in new Setup {
      underTest.apiRequiresTrust(data.doesRequireTrust()) shouldBe true
    }
    "return false when the api does not require trust" in new Setup {
      underTest.apiRequiresTrust(data.doesNotRequireTrust()) shouldBe false
    }
    "return false when the api does not specify trust" in new Setup {
      underTest.apiRequiresTrust(data.trustNotSpecified()) shouldBe false
    }
  }

  "apiHasActiveVersions" should {
    "return true when at least one is active" in new Setup {
      val data = testDefn(serviceName, apiVersion1.asRETIRED,apiVersion2)

      underTest.apiHasActiveVersions(data) shouldBe true
    }
    "return false when at no version is active" in new Setup {
      val data = testDefn(serviceName, apiVersion1.asRETIRED,apiVersion2.asRETIRED)

      underTest.apiHasActiveVersions(data) shouldBe false
    }
  }

  "comineDefinitions" should {

    "return empty wher local and remote fail to return any definitions" in new Setup {
      val result = underTest.combineDefintions(Seq.empty, Seq.empty)

      result shouldBe Seq.empty
    }

    "return definitions ordered by name" in new Setup {
      val data1 = testDefn("A", apiVersion1).withName("A")
      val data2 = testDefn("B", apiVersion1).withName("C")
      val data3 = testDefn("B", apiVersion1).withName("B")

      val result = underTest.combineDefintions(Seq.empty, Seq(data1,data2,data3))

      result.map(_.name) shouldBe Seq("A", "B", "C")
    }

    "returns definitions filtered by active versions" in new Setup {
      val data1 = testDefn("AllRetired", apiVersion1.asRETIRED,apiVersion2.asRETIRED)
      val data2 = testDefn("SomeRetired", apiVersion1.asRETIRED, apiVersion2)

      val result = underTest.combineDefintions(Seq.empty, Seq(data1, data2))
      result.map(_.serviceName) should not contain ("AllRetired")
      result.map(_.serviceName) should contain ("SomeRetired")
    }

    "returns definitions filtered by trust" in new Setup {
      val data1 = testDefn("TrustRequired", apiVersion1).doesRequireTrust()
      val data2 = testDefn("NoTrustRequired", apiVersion1).trustNotSpecified()

      val result = underTest.combineDefintions(Seq.empty, Seq(data1, data2))
      result.map(_.serviceName) should not contain ("TrustRequired")
      result.map(_.serviceName) should contain ("NoTrustRequired")
    }

    "returns subordinate definitions but not principals that are also in subordinate" in new Setup {
      val data1 = testDefn("A", apiVersion1).withName("A")
      val data2 = testDefn("B", apiVersion1).withName("B")
      val data3 = testDefn("C", apiVersion1).withName("C")

      val result =  underTest.combineDefintions(Seq(data1, data2), Seq(data2, data3))
      result should have size(3)

      result.map(_.name) shouldBe Seq("A","B","C")
    }
  }
}
