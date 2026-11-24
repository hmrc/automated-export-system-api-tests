/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.api.specs
package codelist

import play.api.libs.json.{JsObject, Json}

class CodeListSpec extends BaseSpec {

  private val codeLists = Seq(
    ("transport-mode", "1", "Sea transport"),
    ("transport-means", "10", "IMO Ship Identification Number"),
    ("nationality", "CI", "Côte d'Ivoire")
  )

  Feature("Code lists") {

    codeLists.foreach { case (path, exampleKey, exampleValue) =>
      Scenario(s"The $path code list returns its valid items without a token") {

        Given(s"the $path code list endpoint")

        When("the code list is requested without a bearer token")

        val response = service.getCodeList(path).futureValue

        Then("the response is 200 with a JSON list")

        response.status shouldBe 200

        val items = Json.parse(response.body).as[Seq[JsObject]]
        items should not be empty

        And("every item has a key and a value")

        items.foreach { item =>
          (item \ "key").asOpt[String].exists(_.nonEmpty)   shouldBe true
          (item \ "value").asOpt[String].exists(_.nonEmpty) shouldBe true
        }

        And(s"the list contains $exampleKey - $exampleValue")

        items.map(item => ((item \ "key").as[String], (item \ "value").as[String])) should contain(
          (exampleKey, exampleValue)
        )
      }
    }
  }
}
