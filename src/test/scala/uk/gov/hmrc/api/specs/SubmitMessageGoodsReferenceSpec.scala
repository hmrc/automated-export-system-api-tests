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
import uk.gov.hmrc.api.helpers.GoodsReferenceXmlBuilder

class SubmitMessageGoodsReferenceSpec extends BaseSpec with BeforeAndAfterAll {

  private var bearerToken: String = _

  override def beforeAll(): Unit =
    bearerToken = service.getBearerToken.futureValue

  Feature("Submit IE507 Message - GoodsReference list (AES-918)") {

    Scenario("Minimal submission with a single GoodsReference entry submits and retrieves correctly") {

      Given("an IE507 payload with exactly one GoodsReference entry")

      val submission = GoodsReferenceXmlBuilder.withCount(1)

      When("the payload is submitted")

      val submitResponse =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("the request is accepted")

      submitResponse.status shouldBe 202

      And("the submission can be retrieved with the GoodsReference entry intact")

      val getResponse =
        service.getSubmission(submission.submissionId, bearerToken).futureValue

      getResponse.status shouldBe 200
      getResponse.body should include("<sequenceNumber>1</sequenceNumber>")
      getResponse.body should include("<declarationGoodsItemNumber>1</declarationGoodsItemNumber>")
    }

    Scenario(
      "Submission with multiple GoodsReference entries is accepted and entries are returned in submitted order"
    ) {

      Given("an IE507 payload with 3 GoodsReference entries")

      val submission = GoodsReferenceXmlBuilder.withCount(3)

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

      val seqPositions =
        Seq(
          "<sequenceNumber>1</sequenceNumber>",
          "<sequenceNumber>2</sequenceNumber>",
          "<sequenceNumber>3</sequenceNumber>"
        ).map(body.indexOf)

      seqPositions should not contain -1
      seqPositions shouldBe sorted
    }

    Scenario("Submission with a high volume of GoodsReference entries within the body-size limit is accepted") {

      Given("an IE507 payload with 700 GoodsReference entries (~99.8KB, under the ~100KB body-size limit)")

      val submission = GoodsReferenceXmlBuilder.withCount(700)

      When("the payload is submitted")

      val response =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("the request is accepted")

      response.status shouldBe 202

      And("the submission can be retrieved with all 700 entries")

      val getResponse =
        service.getSubmission(submission.submissionId, bearerToken).futureValue

      getResponse.status shouldBe 200
      getResponse.body should include("<sequenceNumber>700</sequenceNumber>")
    }

    Scenario("Submission exceeding the request body-size limit is rejected") {

      Given("an IE507 payload with 1000 GoodsReference entries (~141.8KB, over the ~100KB body-size limit)")

      val submission = GoodsReferenceXmlBuilder.withCount(1000)

      When("the payload is submitted")

      val response =
        service.submitMessage(submission.xml, bearerToken).futureValue

      Then("a request entity too large response is returned")

      response.status shouldBe 413
      response.body should include("Request Entity Too Large")
    }
  }
}
