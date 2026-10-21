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

import uk.gov.hmrc.api.helpers.*

import java.util.UUID

/** End to end: submission -> stubs (IE917) -> notifications (ErrorMapper) -> backend -> GET /submission/:id. */
class AsyncErrorMappingSpec extends BaseSpec {

  private val AwaitingStatus = "0"
  private val RejectedStatus = "4"

  private val OfficeOfExitPath         = "Body.CustomsOfficeOfExitActual.referenceNumber"
  private val ParentUcrIdPath          = "Body.GoodsShipment.Consignment.parentUCRID"
  private val NumberOfSealsPath        = "Body.GoodsShipment.Consignment.TransportEquipment.numberOfSeals"
  private val AdditionalIdentifierPath = "Body.GoodsShipment.Consignment.LocationOfGoods.additionalIdentifier"
  private val GrossMassPath            = "Body.GoodsShipment.GoodsItem.Commodity.GoodsMeasure.grossMass"
  private val NetMassPath              = "Body.GoodsShipment.GoodsItem.Commodity.GoodsMeasure.netMass"

  private val MucrPrefix = "GB/000000000000-123"

  /** @param blockedByStubBug the stubs cannot trigger this code yet (wrong paths in XmlRequestBodyExtractor) */
  private final case class Ie917Case(
    rawCode: Int,
    expectedCode: String,
    expectedDescription: String,
    expectedPath: String,
    triggerValue: String,
    fields: String => AsyncTriggerFields,
    blockedByStubBug: Boolean = false
  )

  private val ie917Cases: Seq[Ie917Case] = Seq(
    Ie917Case(
      12,
      "INCORRECT_ENUMERATION",
      "A field contains a value that is not permitted from the allowed list.",
      OfficeOfExitPath,
      "GB000000",
      id => AsyncTriggerFields(id, officeOfExit = "GB000000"),
      blockedByStubBug = true
    ),
    Ie917Case(
      13,
      "MISSING",
      "A mandatory field has not been provided.",
      ParentUcrIdPath,
      s"${MucrPrefix}00",
      id => AsyncTriggerFields(id, parentUcrId = Some(s"${MucrPrefix}00"))
    ),
    Ie917Case(
      15,
      "NOT_SUPPORTED_IN_POSITION",
      "The field or value is not valid in this part of the message.",
      NumberOfSealsPath,
      "40",
      id => AsyncTriggerFields(id, numberOfSeals = Some(40))
    ),
    Ie917Case(
      18,
      "UNSPECIFIED_ERROR_OTHER",
      "An unexpected error occurred that does not match a specific validation category.",
      ParentUcrIdPath,
      s"${MucrPrefix}11",
      id => AsyncTriggerFields(id, parentUcrId = Some(s"${MucrPrefix}11"))
    ),
    Ie917Case(
      35,
      "TOO_MANY_REPETITIONS",
      "Too many occurrences of the same element were provided.",
      ParentUcrIdPath,
      s"${MucrPrefix}22",
      id => AsyncTriggerFields(id, parentUcrId = Some(s"${MucrPrefix}22"))
    ),
    Ie917Case(
      39,
      "ELEMENT_TOO_LONG",
      "A field contains more characters than allowed.",
      AdditionalIdentifierPath,
      "98",
      id => AsyncTriggerFields(id, additionalIdentifier = Some("98"))
    ),
    Ie917Case(
      40,
      "ELEMENT_TOO_SHORT",
      "A field contains fewer characters than required.",
      AdditionalIdentifierPath,
      "12",
      id => AsyncTriggerFields(id, additionalIdentifier = Some("12"))
    ),
    Ie917Case(
      50,
      "INVALID_VALUE_SPECIFICTYPE",
      "The value provided is not valid for the expected data type.",
      OfficeOfExitPath,
      "GB000111",
      id => AsyncTriggerFields(id, officeOfExit = "GB000111"),
      blockedByStubBug = true
    ),
    Ie917Case(
      51,
      "INVALID_VALUE_PATTERN",
      "The value does not match the required format or pattern.",
      NumberOfSealsPath,
      "50",
      id => AsyncTriggerFields(id, numberOfSeals = Some(50))
    ),
    Ie917Case(
      52,
      "INVALID_XML_FORMAT",
      "The message XML structure is invalid and cannot be processed.",
      ParentUcrIdPath,
      s"${MucrPrefix}33",
      id => AsyncTriggerFields(id, parentUcrId = Some(s"${MucrPrefix}33"))
    ),
    Ie917Case(
      53,
      "INVALID_CHARACTERS",
      "The message contains unsupported or invalid characters.",
      ParentUcrIdPath,
      s"${MucrPrefix}44",
      id => AsyncTriggerFields(id, parentUcrId = Some(s"${MucrPrefix}44"))
    ),
    Ie917Case(
      54,
      "VALUE_LOWER_ALLOWED_LOWEST_LIMIT",
      "The value is below the minimum allowed limit.",
      GrossMassPath,
      "2.1",
      id => AsyncTriggerFields(id, grossMass = Some("2.1"), netMass = Some("1.0")),
      blockedByStubBug = true
    ),
    Ie917Case(
      55,
      "VALUE_GREATER_ALLOWED_UPPER_LIMIT",
      "The value exceeds the maximum allowed limit.",
      NetMassPath,
      "88.8",
      id => AsyncTriggerFields(id, netMass = Some("88.8")),
      blockedByStubBug = true
    ),
    Ie917Case(
      56,
      "VALUE_LOWER_OR_EQUAL_ALLOWED_LOWEST_LIMIT",
      "The value must be greater than the minimum allowed limit.",
      GrossMassPath,
      "1.1",
      id => AsyncTriggerFields(id, grossMass = Some("1.1"), netMass = Some("1.0")),
      blockedByStubBug = true
    ),
    Ie917Case(
      57,
      "VALUE_GREATER_OR_EQUAL_ALLOWED_LOWEST_LIMIT",
      "The value must be less than the maximum allowed limit.",
      NetMassPath,
      "99.9",
      id => AsyncTriggerFields(id, netMass = Some("99.9")),
      blockedByStubBug = true
    )
  )

  private def verify(testCase: Ie917Case): Unit = {
    val eori         = TestEori.random()
    val token        = service.getBearerToken(TestEori.authRequest(eori)).futureValue
    val submissionId = UUID.randomUUID().toString

    val response =
      service
        .submitMessageWithCorrelationId(
          AsyncPayloadBuilder.build(testCase.fields(submissionId)),
          token,
          UUID.randomUUID().toString.replace("-", "")
        )
        .futureValue

    response.status shouldBe 202

    // Only wait until the async response has been processed (status is no longer AWAITING).
    // The result is then checked once, so a wrong result fails straight away
    // instead of being retried until the patience timeout.
    val body =
      eventually {
        val submission = service.getSubmission(submissionId, token).futureValue
        submission.status shouldBe 200

        val status = SubmissionXml.tagValue(submission.body, "status")
        status should not be empty
        status should not be Some(AwaitingStatus)

        submission.body
      }

    SubmissionXml.tagValue(body, "status") shouldBe Some(RejectedStatus)

    SubmissionXml.errors(body) shouldBe Seq(
      SubmissionXml.ReturnedError(
        code = Some(testCase.expectedCode),
        description = Some(testCase.expectedDescription),
        path = Some(testCase.expectedPath)
      )
    )
  }

  Feature("IE917 XML error codes are returned with the mapped AES code, description and path") {

    ie917Cases.foreach { testCase =>
      Scenario(
        s"IE917 error code ${testCase.rawCode} is returned as ${testCase.expectedCode}",
        AsyncNotificationTag
      ) {

        Given(s"a submission that makes the stubs return IE917 error code ${testCase.rawCode}")

        When("it is submitted and the async response has been processed")

        Then("GET submission returns status REJECTED (4) with the mapped error")

        // Stub bug: XmlRequestBodyExtractor uses Body.CustomsOfficeOExitActual and Body.GoodsItem,
        // so codes 12, 50 and 54-57 are never triggered. Replace this pending with verify(testCase) once the stubs are fixed.
        if (testCase.blockedByStubBug) pending
        else verify(testCase)
      }
    }
  }
}
