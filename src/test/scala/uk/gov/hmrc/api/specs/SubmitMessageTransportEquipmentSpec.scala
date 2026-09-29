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
import uk.gov.hmrc.api.helpers.TransportEquipmentXmlBuilder

/** AES-917: Automated API tests for the TransportEquipment list on IE507A submissions.
  *
  * NOTE ON THE 9999/10000 ACCEPTANCE CRITERIA:
  * The IE507A XSD allows up to 9999 TransportEquipment entries (maxOccurs="9999"), and the
  * ticket's AC describes accepting a submission at that limit and rejecting one above it.
  * In practice this boundary cannot be exercised through the API: the AES service's request
  * body size limit (Play's default `play.http.parser.maxMemoryBuffer`, ~100KB) is reached at
  * around 500-600 TransportEquipment entries - long before the schema's entry-count limit -
  * and the request is rejected with 413 (Request Entity Too Large) before entry-count
  * validation ever runs. This was confirmed by probing payload sizes from 500 to 9999 entries:
  *   500 entries  (~99.9KB)  -> 202 Accepted
  *   1000 entries (~198.4KB) -> 413 Request Entity Too Large
  * These tests therefore verify the closest practically-reachable behaviour instead of the
  * literal 9999/10000 boundary. Flagged to the team separately for a decision on whether to
  * raise the body-size limit or clarify the AC.
  */
class SubmitMessageTransportEquipmentSpec extends BaseSpec with BeforeAndAfterAll {

  private var bearerToken: String = _

  override def beforeAll(): Unit =
    bearerToken = service.getBearerToken.futureValue

  Feature("Submit IE507 Message - TransportEquipment list (AES-917)") {

    Scenario("Minimal submission with a single TransportEquipment entry submits and retrieves correctly") {

      Given("an IE507 payload with exactly one TransportEquipment entry")

      val submission = TransportEquipmentXmlBuilder.withCount(1)

      When("the payload is submitted")

      val submitResponse =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("the request is accepted")

      submitResponse.status shouldBe 202

      And("the submission can be retrieved with the TransportEquipment entry intact")

      val getResponse =
        service.getSubmission(submission.submissionId, bearerToken).futureValue

      getResponse.status shouldBe 200
      getResponse.body should include("<sequenceNumber>1</sequenceNumber>")
      getResponse.body should include("<containerIdentificationNumber>CONT1</containerIdentificationNumber>")
    }

    Scenario(
      "Submission with multiple TransportEquipment entries is accepted and entries are returned in submitted order"
    ) {

      Given("an IE507 payload with 3 TransportEquipment entries")

      val submission = TransportEquipmentXmlBuilder.withCount(3)

      When("the payload is submitted")

      val submitResponse =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("the request is accepted")

      submitResponse.status shouldBe 202

      And("GET returns all 3 entries, in the order they were submitted")

      val getResponse =
        service.getSubmission(submission.submissionId, bearerToken).futureValue

      getResponse.status shouldBe 200

      val body = getResponse.body

      val containerPositions =
        Seq("CONT1", "CONT2", "CONT3").map(body.indexOf)

      containerPositions should not contain -1
      containerPositions shouldBe sorted

      val seqPositions =
        Seq(
          "<sequenceNumber>1</sequenceNumber>",
          "<sequenceNumber>2</sequenceNumber>",
          "<sequenceNumber>3</sequenceNumber>"
        ).map(body.indexOf)

      seqPositions should not contain -1
      seqPositions shouldBe sorted
    }

    Scenario("Submission with a high volume of TransportEquipment entries within the body-size limit is accepted") {

      Given("an IE507 payload with 500 TransportEquipment entries (~99.9KB, under the ~100KB body-size limit)")

      val submission = TransportEquipmentXmlBuilder.withCount(500)

      When("the payload is submitted")

      val response =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("the request is accepted")

      response.status shouldBe 202

      And("the submission can be retrieved with all 500 entries")

      val getResponse =
        service.getSubmission(submission.submissionId, bearerToken).futureValue

      getResponse.status shouldBe 200
      getResponse.body should include("<sequenceNumber>500</sequenceNumber>")
    }

    Scenario("Submission exceeding the request body-size limit is rejected") {

      Given("an IE507 payload with 1000 TransportEquipment entries (~198KB, over the ~100KB body-size limit)")

      val submission = TransportEquipmentXmlBuilder.withCount(1000)

      When("the payload is submitted")

      val response =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("a request entity too large response is returned")

      response.status shouldBe 413
      response.body should include("Request Entity Too Large")
    }
  }
}
