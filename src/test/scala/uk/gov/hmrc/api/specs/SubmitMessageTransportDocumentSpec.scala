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
import uk.gov.hmrc.api.helpers.TransportDocumentXmlBuilder

class SubmitMessageTransportDocumentSpec extends BaseSpec with BeforeAndAfterAll {

  private var bearerToken: String = _

  override def beforeAll(): Unit =
    bearerToken = service.getBearerToken.futureValue

  Feature("Submit IE507 Message - TransportDocument list (AES-916)") {

    Scenario("Minimal submission with a single TransportDocument entry submits and retrieves correctly") {

      Given("an IE507 payload with one TransportDocument entry")

      val submission = TransportDocumentXmlBuilder.withCount(1)

      When("the payload is submitted")

      val submitResponse =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("the request is accepted")

      submitResponse.status shouldBe 202

      And("the submission can be retrieved with the TransportDocument entry intact")

      val getResponse =
        service.getSubmission(submission.submissionId, bearerToken).futureValue

      getResponse.status shouldBe 200
      getResponse.body     should include("<sequenceNumber>1</sequenceNumber>")
      getResponse.body     should include("<type>1</type>")
      getResponse.body     should include("<referenceNumber>REF-1</referenceNumber>")
    }

    Scenario(
      "Submission with multiple TransportDocument entries is accepted and entries are returned in submitted order"
    ) {

      Given("an IE507 payload with 4 TransportDocument entries")

      val submission = TransportDocumentXmlBuilder.withCount(4)

      When("the payload is submitted")

      val submitResponse =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("the request is accepted")

      submitResponse.status shouldBe 202

      And("GET returns all 4 entries, in the order they were submitted")

      val getResponse =
        service.getSubmission(submission.submissionId, bearerToken).futureValue

      getResponse.status shouldBe 200

      val body = getResponse.body

      val referencePositions =
        Seq(
          "REF-1",
          "REF-2",
          "REF-3",
          "REF-4"
        ).map(body.indexOf)

      referencePositions   should not contain -1
      referencePositions shouldBe sorted

      val seqPositions =
        Seq(
          "<sequenceNumber>1</sequenceNumber>",
          "<sequenceNumber>2</sequenceNumber>",
          "<sequenceNumber>3</sequenceNumber>",
          "<sequenceNumber>4</sequenceNumber>"
        ).map(body.indexOf)

      seqPositions   should not contain -1
      seqPositions shouldBe sorted
    }

    Scenario("Submission with 99 TransportDocument entries (at limit) is accepted") {

      Given("an IE507 payload with exactly 99 TransportDocument entries")

      val submission = TransportDocumentXmlBuilder.withCount(99)

      When("the payload is submitted")

      val response =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("the request is accepted")

      response.status shouldBe 202

      And("the submission can be retrieved with all 99 TransportDocument entries")

      val getResponse =
        service.getSubmission(submission.submissionId, bearerToken).futureValue

      getResponse.status shouldBe 200
      getResponse.body     should include("<sequenceNumber>99</sequenceNumber>")
      getResponse.body     should include("<referenceNumber>REF-99</referenceNumber>")
    }

    Scenario("Submission with 100 TransportDocument entries (over limit) is rejected") {

      Given("an IE507 payload with 100 TransportDocument entries")

      val submission = TransportDocumentXmlBuilder.withCount(100)

      When("the payload is submitted")

      val response =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("a bad request response is returned")

      response.status shouldBe 400
      response.body     should include("BAD_REQUEST")
      response.body     should include("TransportDocument")
    }
  }

}
