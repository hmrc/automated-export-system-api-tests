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

import java.util.UUID
import org.scalatest.BeforeAndAfterAll
import uk.gov.hmrc.api.helpers.PayloadLoader

class GetSubmissionsSpec extends BaseSpec with BeforeAndAfterAll {

  private var bearerToken: String = _

  override def beforeAll(): Unit = {
    bearerToken = service.getBearerToken.futureValue

    val xml =
      PayloadLoader.load("valid-ie507.xml")

    service
      .submitMessage(
        xml,
        bearerToken
      )
      .futureValue
  }

  Feature("Get Submissions") {

    Scenario("Authenticated user can retrieve submissions") {

      Given("a valid bearer token")

      When("the submissions endpoint is called")

      val response =
        service
          .getSubmissions(bearerToken)
          .futureValue

      Then("a successful response is returned")

      response.status shouldBe 200

      And("the response contains the submissions root element")

      response.body should include("<Submissions>")

      And("the response contains submission records")

      response.body should include("<Submission>")

      And("the response contains submission identifiers")

      response.body should include("<submissionId>")
    }

    Scenario("Retrieving submissions without a bearer token returns 401") {

      Given("no bearer token")

      When("the submissions endpoint is called without authentication")

      val response =
        service.getSubmissionsWithoutAuth.futureValue

      Then("an unauthorised response is returned")

      response.status shouldBe 401
    }

    Scenario("Retrieving submissions with an invalid bearer token returns 401") {

      Given("a garbled bearer token")

      val invalidToken =
        "not-a-valid-token-" + UUID.randomUUID().toString

      When("the submissions endpoint is called with that token")

      val response =
        service
          .getSubmissions(invalidToken)
          .futureValue

      Then("an unauthorised response is returned")

      response.status shouldBe 401
    }
  }
}
