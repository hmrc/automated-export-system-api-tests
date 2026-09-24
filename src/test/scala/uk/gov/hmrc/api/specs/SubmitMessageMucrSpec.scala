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

import org.scalatest.BeforeAndAfterAll
import uk.gov.hmrc.api.helpers.PayloadLoader

/*
 * E2E_11413_TC17 - IE507 submitted with 'parent_UCR_id' populated with a
 * valid MUCR, no discrepancies, no split exit (isolated MUCR association,
 * not combined with the discrepancies flow). Scripting owner: AppDev.
 *
 * This covers our side only: submitting an IE507 with a MUCR reference and
 * confirming it is accepted and correctly persisted. The actual
 * consolidation logic (matching/linking within CDS/ILE.Core) happens
 * downstream and is outside our visibility - verified separately by SI.
 */
class SubmitMessageMucrSpec extends BaseSpec with BeforeAndAfterAll {

  private var bearerToken: String = _

  // Keep in sync with the parentUCRID value in valid-ie507-mucr.xml
  private val expectedMucr = "GB/000000000000-12345"

  override def beforeAll(): Unit =
    bearerToken = service.getBearerToken.futureValue

  Feature("Submit IE507 Message with MUCR association (TC17)") {

    Scenario("Valid IE507 with a MUCR reference is accepted") {

      Given("a valid IE507 XML payload with parentUCRID populated and a valid bearer token")

      val xml =
        PayloadLoader.load("valid-ie507-mucr.xml")

      When("the payload is submitted to the AES message endpoint")

      val response =
        service
          .submitMessage(
            xml,
            bearerToken
          )
          .futureValue

      Then("the request is accepted")

      response.status shouldBe 202
    }

    Scenario("The submitted MUCR is correctly persisted and retrievable") {

      Given("a valid IE507 XML payload with parentUCRID populated and a valid bearer token")

      val xml =
        PayloadLoader.load("valid-ie507-mucr.xml")

      And("the payload has already been submitted")

      service
        .submitMessage(
          xml,
          bearerToken
        )
        .futureValue

      When("the submissions list is retrieved")

      val submissionsResponse =
        service
          .getSubmissions(bearerToken)
          .futureValue

      val submissionId =
        "<submissionId>(.*?)</submissionId>".r
          .findFirstMatchIn(submissionsResponse.body)
          .map(_.group(1))
          .getOrElse(
            throw new RuntimeException(
              "No submissionId found in submissions list - cannot proceed with assertion"
            )
          )

      And("that submission is retrieved by id")

      val response =
        service
          .getSubmission(submissionId, bearerToken)
          .futureValue

      Then("a successful response is returned")

      response.status shouldBe 200

      // NOTE: assumes the GET submission response echoes the same
      // <parentUCRID> tag used on the way in (per Consignment's XML
      // writer in the backend). Verify this against a real run before
      // relying on it in CI - update the tag/assertion if the response
      // shape differs.
      And("the response contains the MUCR that was submitted")

      response.body should include(s"<parentUCRID>$expectedMucr</parentUCRID>")
    }
  }
}