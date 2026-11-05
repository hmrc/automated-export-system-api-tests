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
package async

import uk.gov.hmrc.api.helpers.*

import java.util.UUID

/** End to end: submission -> stubs (IE906) -> notifications (ErrorMapper) -> backend -> GET /submission/:id. */
class AsyncIe906ErrorMappingSpec extends BaseSpec {

  private val RejectedStatus = "4"

  private val TechnicalError = "There is a problem with the service"

  private val MrnPath              = "/CC507C/Body/ExportOperation/MRN"
  private val TypeOfLocationPath   = "/CC507C/Body/GoodsShipment/Consignment/LocationOfGoods/typeOfLocation"
  private val AuthorisationNumPath = "/CC507C/Body/GoodsShipment/Consignment/LocationOfGoods/authorisationNumber"
  private val ParentUcrIdPath      = "/CC507C/Body/GoodsShipment/Consignment/parentUCRID"

  private val MucrPrefix = "GB/000000000000-123"

  private final case class Ie906Case(
    rawCode: Int,
    expectedCode: String,
    expectedDescription: String,
    expectedPath: String,
    payload: String => String
  )

  private def basePayload(submissionId: String): String =
    AsyncPayloadBuilder.build(AsyncTriggerFields(submissionId))

  private def withMrn(mrn: String): String => String =
    id => AsyncPayloadBuilder.build(AsyncTriggerFields(id, mrn = mrn))

  private def withTagValue(tag: String, value: String): String => String =
    id => {
      val xml = basePayload(id)
      withClue(s"The payload has no <$tag> element: ")(xml should include(s"<$tag>"))
      xml.replaceAll(s"<$tag>[^<]*</$tag>", s"<$tag>$value</$tag>")
    }

  // MRN used by the stubs: IE906Rules look at the ending of the MRN
  private def mrnEnding(ending: String): String = s"26GB0000X6524786$ending"

  private val ie906Cases: Seq[Ie906Case] = Seq(
    Ie906Case(12, "CODELIST_VIOLATION", TechnicalError, TypeOfLocationPath, withTagValue("typeOfLocation", "E")),
    Ie906Case(
      13,
      "CONDITION_VIOLATION_MISSING",
      TechnicalError,
      AuthorisationNumPath,
      withTagValue("authorisationNumber", "1104")
    ),
    Ie906Case(14, "RULE_VIOLATION", TechnicalError, AuthorisationNumPath, withTagValue("authorisationNumber", "1103")),
    Ie906Case(
      15,
      "CONDITION_VIOLATION_NOT_ALLOWED",
      TechnicalError,
      AuthorisationNumPath,
      withTagValue("authorisationNumber", "1100")
    ),
    Ie906Case(
      26,
      "DUPLICATED_MESSAGE_ID",
      TechnicalError,
      AuthorisationNumPath,
      withTagValue("authorisationNumber", "1101")
    ),
    Ie906Case(
      27,
      "ROLEBASED_AUTH_FAILED",
      TechnicalError,
      AuthorisationNumPath,
      withTagValue("authorisationNumber", "1102")
    ),
    Ie906Case(
      35,
      "MUCR_SHUT",
      "MUCR can no longer be updated",
      ParentUcrIdPath,
      id => AsyncPayloadBuilder.build(AsyncTriggerFields(id, parentUcrId = Some(s"${MucrPrefix}A1")))
    ),
    Ie906Case(50, "TRANSITIONAL_CONSTRAINT_VIOLATION", TechnicalError, MrnPath, withMrn(mrnEnding("B3"))),
    Ie906Case(51, "EDI_VIOLATION_POST_DOWNGRADE", TechnicalError, MrnPath, withMrn(mrnEnding("B2"))),
    Ie906Case(52, "FUNCTIONAL_VIOLATION_POST_DOWNGRADE", TechnicalError, MrnPath, withMrn(mrnEnding("B1"))),
    Ie906Case(90, "UNKNOWN_MRN", "MRN not found", MrnPath, withMrn(mrnEnding("B0"))),
    Ie906Case(93, "INVALID_MRN", "MRN is not valid", MrnPath, withMrn(mrnEnding("A4"))),
    Ie906Case(94, "INVALID_DISCREPANCIES", "Discrepancies mismatch", MrnPath, withMrn(mrnEnding("B4"))),
    Ie906Case(95, "INVALID_AMENDMENT", "Invalid amendment", MrnPath, withMrn(mrnEnding("B5")))
  )

  // Stub bugs: IE906Rules read paths that do not exist in the IE507A message, so these codes are never triggered.
  // - 8, 96-99: Body.GoodsShipment.Consignment.ReferenceNumberUCRID (the message has referenceNumberUCR)
  // - 92: Body.GoodsShipment.GoodsItem.Commodity.Packaging.sequenceNumber (Packaging is directly under GoodsItem)
  // Replace each pending with a real case once the stubs are fixed.
  private val blockedByStubBug: Seq[(Int, String)] = Seq(
    8  -> "INVALID_FORMAT_DUCR",
    92 -> "MESSAGE_OUT_OF_SEQUENCE",
    96 -> "DIVERSION_REJECTED_INVALID_DECLARATION",
    97 -> "DIVERSION_REJECTED_UNKNOWN_MRN",
    98 -> "DIVERSION_REJECTED_ALREADY_EXITED",
    99 -> "DIVERSION_REJECTED_OTHER"
  )

  private def verify(testCase: Ie906Case): Unit = {
    val eori         = TestEori.random()
    val token        = service.getBearerToken(TestEori.authRequest(eori)).futureValue
    val submissionId = UUID.randomUUID().toString

    val response =
      service
        .submitMessageWithCorrelationId(
          testCase.payload(submissionId),
          token,
          UUID.randomUUID().toString.replace("-", "")
        )
        .futureValue

    response.status shouldBe 202

    // Wait for the IE906 to be processed: the submission becomes REJECTED (4).
    // The errors are then checked once.
    val body =
      eventually {
        val submission = service.getSubmission(submissionId, token).futureValue
        submission.status                                 shouldBe 200
        SubmissionXml.tagValue(submission.body, "status") shouldBe Some(RejectedStatus)
        submission.body
      }

    SubmissionXml.errors(body) shouldBe Seq(
      SubmissionXml.ReturnedError(
        code = Some(testCase.expectedCode),
        description = Some(testCase.expectedDescription),
        path = Some(testCase.expectedPath)
      )
    )
  }

  Feature("IE906 functional error codes are returned with the mapped AES code, description and path") {

    ie906Cases.foreach { testCase =>
      Scenario(
        s"IE906 error code ${testCase.rawCode} is returned as ${testCase.expectedCode}",
        AsyncNotificationTag
      ) {

        Given(s"a submission that makes the stubs return IE906 error code ${testCase.rawCode}")

        When("it is submitted and the async response has been processed")

        Then("GET submission returns status REJECTED (4) with the mapped error")

        verify(testCase)
      }
    }

    blockedByStubBug.foreach { case (rawCode, expectedCode) =>
      Scenario(s"IE906 error code $rawCode is returned as $expectedCode", AsyncNotificationTag) {
        pending
      }
    }
  }
}
