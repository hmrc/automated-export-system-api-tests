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
package validation

import uk.gov.hmrc.api.helpers.*

import java.util.UUID

/** Validation and error handling for POST /message (IE507A). Expected values come from the real backend behaviour (curl
  * matrix, 3 Nov 2026).
  */
class SubmitMessageValidationSpec extends BaseSpec {

  private def newSubmissionId(): String = UUID.randomUUID().toString

  private def validPayload(
    submissionId: String = newSubmissionId(),
    discrepanciesExist: Int = 0
  ): String =
    Ie507PayloadBuilder.build(Ie507Fields(submissionId = submissionId, discrepanciesExist = discrepanciesExist))

  private def newToken(): String = {
    val eori = TestEori.random()
    service.getBearerToken(TestEori.authRequest(eori)).futureValue
  }

  private val SchemaFailureMessage = "XML failed schema validation"
  private val EisFailureMessage    = "Failed to submit IE507 message to EIS"

  Feature("Mandatory field and invalid payload validation (XSD)") {

    final case class XsdCase(name: String, payload: () => String)

    val xsdCases = Seq(
      XsdCase("MRN is missing", () => validPayload().replaceAll("(?s)\\s*<MRN>[^<]*</MRN>", "")),
      XsdCase("MRN does not match the pattern", () => validPayload().replaceAll("<MRN>[^<]*</MRN>", "<MRN>ABC</MRN>")),
      XsdCase("discrepanciesExist has a value that is not allowed", () => validPayload(discrepanciesExist = 2))
    )

    xsdCases.foreach { testCase =>
      Scenario(s"A submission where ${testCase.name} returns 400 with schema validation details") {

        Given("an authenticated trader")
        val token = newToken()

        When("a payload that breaks the schema is submitted")
        val response = service.submitMessage(testCase.payload(), token).futureValue

        Then("the response is 400 BAD_REQUEST with the schema validation message")
        response.status                                 shouldBe 400
        SubmissionXml.tagValue(response.body, "status") shouldBe Some("400")
        SubmissionXml.tagValue(response.body, "code")   shouldBe Some("BAD_REQUEST")
        response.body                                     should include(SchemaFailureMessage)

        And("the response lists the failing line, column and message")
        response.body should include("<errors>")
        response.body should include("<line>")
        response.body should include("<column>")
        response.body should include("<message>")
      }
    }

    Scenario("A payload that is not well formed XML returns 400 without schema error details") {

      Given("an authenticated trader")
      val token = newToken()

      When("a broken XML document is submitted")
      val response = service.submitMessage("<aes:Submission><broken>", token).futureValue

      Then("the response is 400 BAD_REQUEST with an Invalid XML message")
      response.status                               shouldBe 400
      SubmissionXml.tagValue(response.body, "code") shouldBe Some("BAD_REQUEST")
      response.body                                   should include("Invalid XML")

      And("there is no schema error list, because the XML never reached the schema validation")
      response.body should not include "<errors>"
    }

    Scenario("An empty request body returns 400") {

      Given("an authenticated trader")
      val token = newToken()

      When("an empty body is submitted")
      val response = service.submitMessage("", token).futureValue

      Then("the response is 400 BAD_REQUEST with an Invalid XML message")
      response.status                               shouldBe 400
      SubmissionXml.tagValue(response.body, "code") shouldBe Some("BAD_REQUEST")
      response.body                                   should include("Invalid XML")
    }

    Scenario("A submission without a submissionId is accepted, because the field is optional") {

      Given("an authenticated trader")
      val token = newToken()

      When("a payload without submissionId is submitted")
      val payload  = validPayload().replaceAll("(?s)\\s*<submissionId>[^<]*</submissionId>", "")
      val response = service.submitMessage(payload, token).futureValue

      Then("the response is 202")
      response.status shouldBe 202
    }
  }

  Feature("Request header validation") {

    Scenario("A submission without a Content-Type header returns 415") {

      Given("an authenticated trader")
      val token = newToken()

      When("a valid payload is submitted without Content-Type")
      val response = service.submitMessageWithoutContentType(validPayload(), token).futureValue

      Then("the response is 415 UNSUPPORTED_MEDIA_TYPE")
      response.status                               shouldBe 415
      SubmissionXml.tagValue(response.body, "code") shouldBe Some("UNSUPPORTED_MEDIA_TYPE")
      response.body                                   should include("Expecting xml body")
    }

    Scenario("A submission with Content-Type text/plain returns 415") {

      Given("an authenticated trader")
      val token = newToken()

      When("a valid payload is submitted as text/plain")
      val response = service.submitMessageWithContentType(validPayload(), token, "text/plain").futureValue

      Then("the response is 415 UNSUPPORTED_MEDIA_TYPE")
      response.status                               shouldBe 415
      SubmissionXml.tagValue(response.body, "code") shouldBe Some("UNSUPPORTED_MEDIA_TYPE")
      response.body                                   should include("Expecting xml body")
    }

    Scenario("A submission with a charset that is not UTF-8 returns 415") {

      Given("an authenticated trader")
      val token = newToken()

      When("a valid payload is submitted with charset ISO-8859-1")
      val response =
        service.submitMessageWithContentType(validPayload(), token, "application/xml; charset=ISO-8859-1").futureValue

      Then("the response is 415 with the charset message")
      response.status                               shouldBe 415
      SubmissionXml.tagValue(response.body, "code") shouldBe Some("UNSUPPORTED_MEDIA_TYPE")
      response.body                                   should include("charset is not UTF-8")
    }

    Scenario("A submission without X-Correlation-Id is accepted, because the backend generates one") {

      Given("an authenticated trader")
      val token = newToken()

      When("a valid payload is submitted without the X-Correlation-Id header")
      val response = service.submitMessage(validPayload(), token).futureValue

      Then("the response is 202")
      response.status shouldBe 202
    }
  }

  Feature("Invalid authentication") {

    Scenario("A submission with an invalid bearer token returns 401") {

      When("a valid payload is submitted with a token the auth service does not know")
      val response = service.submitMessage(validPayload(), "abc.def").futureValue

      Then("the response is 401")
      response.status shouldBe 401
    }

    Scenario("A submission with an empty bearer token returns 401") {

      When("a valid payload is submitted with an empty token")
      val response = service.submitMessage(validPayload(), "").futureValue

      Then("the response is 401")
      response.status shouldBe 401
    }
  }

  Feature("Duplicate submissions") {

    Scenario("The same trader submitting the same submissionId again is accepted") {

      Given("a trader who already submitted a message")
      val token        = newToken()
      val submissionId = newSubmissionId()
      service.submitMessage(validPayload(submissionId), token).futureValue.status shouldBe 202

      When("the same submissionId is submitted again")
      val response = service.submitMessage(validPayload(submissionId), token).futureValue

      Then("the response is 202")
      response.status shouldBe 202
    }

    Scenario("A different trader using an existing submissionId gets 404 and cannot see the original") {

      Given("trader A has a submission")
      val tokenA       = newToken()
      val tokenB       = newToken()
      val submissionId = newSubmissionId()
      service.submitMessage(validPayload(submissionId), tokenA).futureValue.status shouldBe 202

      When("trader B submits the same submissionId")
      val response = service.submitMessage(validPayload(submissionId), tokenB).futureValue

      Then("the response is 404 NOT_FOUND")
      response.status                               shouldBe 404
      SubmissionXml.tagValue(response.body, "code") shouldBe Some("NOT_FOUND")
      response.body                                   should include("Submission not found")

      And("trader B cannot read trader A's submission")
      service.getSubmission(submissionId, tokenB).futureValue.status shouldBe 404

      And("trader A's submission is still retrievable")
      service.getSubmission(submissionId, tokenA).futureValue.status shouldBe 200
    }
  }

  Feature("Synchronous EIS errors (MRN based stub triggers)") {

    // The stubs return 401, 404, 500 or 400 for these MRN endings, but the backend answers 500 for all of them.
    Seq("A0", "A1", "A2", "A3").foreach { ending =>
      Scenario(s"An EIS error triggered by MRN ending $ending is returned as a generic 500") {

        Given("an authenticated trader")
        val token = newToken()

        When(s"a payload whose MRN ends with $ending is submitted")
        val payload  = validPayload().replaceAll("A9</MRN>", s"$ending</MRN>")
        val response = service.submitMessage(payload, token).futureValue

        Then("the response is 500 INTERNAL_SERVER_ERROR with the generic EIS failure message")
        response.status                               shouldBe 500
        SubmissionXml.tagValue(response.body, "code") shouldBe Some("INTERNAL_SERVER_ERROR")
        response.body                                   should include(EisFailureMessage)
      }
    }

    // Pending: waiting for the stub timestamp fix and the developer's answer about the expected behaviour
    // (should the EIS status code 401/404/500/400 be returned to the caller?).
    Scenario("Each EIS error is returned with its own status code and error body") {
      pending
    }
  }
}
