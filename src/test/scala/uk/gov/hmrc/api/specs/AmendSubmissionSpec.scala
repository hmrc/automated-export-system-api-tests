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
import uk.gov.hmrc.api.helpers.{Ie507Fields, Ie507PayloadBuilder, NotificationPayloadBuilder}
import uk.gov.hmrc.api.models.{AuthStubEnrolment, AuthStubIdentifier, AuthStubRequest}
import uk.gov.hmrc.http.HttpResponse

import java.util.UUID

class AmendSubmissionSpec extends BaseSpec with BeforeAndAfterAll {

  // Default EORI used by AuthStubRequest
  private val Eori = "GB12345678"

  // Status 1 = Accepted (set after the stubs send the acceptance notification)
  private val AcceptedStatus = "1"

  private var bearerToken: String = _

  override def beforeAll(): Unit =
    bearerToken = service.getBearerToken.futureValue

  private def newCorrelationId(): String =
    UUID.randomUUID().toString.replace("-", "")

  private def tagValue(body: String, tag: String): Option[String] =
    s"<$tag>(.*?)</$tag>".r.findFirstMatchIn(body).map(_.group(1))

  /** Waits until the stubs have accepted the submission (status 1). */
  private def awaitAccepted(submissionId: String): HttpResponse =
    eventually {
      val response = service.getSubmission(submissionId, bearerToken).futureValue
      response.status                   shouldBe 200
      tagValue(response.body, "status") shouldBe Some(AcceptedStatus)
      response
    }

  /** Creates a new submission, waits until it is accepted and returns its submissionId. */
  private def createAcceptedSubmission(): String = {
    val submissionId = UUID.randomUUID().toString

    service
      .submitMessageWithCorrelationId(
        Ie507PayloadBuilder.build(Ie507Fields(submissionId)),
        bearerToken,
        newCorrelationId()
      )
      .futureValue
      .status shouldBe 202

    awaitAccepted(submissionId)
    submissionId
  }

  private def amendedFields(submissionId: String): Ie507Fields =
    Ie507Fields(submissionId, discrepanciesExist = 1, splitIndicator = 1, shippingMarks = "AMENDED01")

  Feature("Amend an existing IE507A submission") {

    Scenario("An existing submission can be amended and the amended details are persisted") {

      Given("an existing, accepted submission")

      val submissionId = createAcceptedSubmission()
      val original     = service.getSubmission(submissionId, bearerToken).futureValue
      tagValue(original.body, "discrepanciesExist") shouldBe Some("0")
      val originalUpdatedAt = tagValue(original.body, "updatedAt")

      When("an amendment is submitted for the same submissionId")

      val amendResponse =
        service
          .submitMessageWithCorrelationId(
            Ie507PayloadBuilder.build(amendedFields(submissionId)),
            bearerToken,
            newCorrelationId()
          )
          .futureValue

      Then("the amendment is accepted")

      amendResponse.status shouldBe 202

      And("the amended submission can be retrieved once processed downstream")

      val amended = awaitAccepted(submissionId)

      And("the response contains all mandatory fields")

      amended.body should include("<Submission>")
      Seq("submissionId", "status", "MRN", "discrepanciesExist", "splitIndicator", "referenceNumber", "updatedAt")
        .foreach(tag => withClue(s"Missing <$tag>: ")(tagValue(amended.body, tag) shouldBe defined))

      And("the amended details are persisted")

      tagValue(amended.body, "submissionId")       shouldBe Some(submissionId)
      tagValue(amended.body, "MRN")                shouldBe Some(Ie507PayloadBuilder.Mrn)
      tagValue(amended.body, "discrepanciesExist") shouldBe Some("1")
      tagValue(amended.body, "splitIndicator")     shouldBe Some("1")
      tagValue(amended.body, "shippingMarks")      shouldBe Some("AMENDED01")
      tagValue(amended.body, "updatedAt")            should not be originalUpdatedAt
    }

    Scenario("Amending a submission does not create a duplicate submission") {

      Given("an existing submission that has been amended")

      val submissionId = createAcceptedSubmission()
      service
        .submitMessageWithCorrelationId(
          Ie507PayloadBuilder.build(amendedFields(submissionId)),
          bearerToken,
          newCorrelationId()
        )
        .futureValue
        .status shouldBe 202
      awaitAccepted(submissionId)

      When("the submissions list is retrieved")

      val list = service.getSubmissions(bearerToken).futureValue

      Then("the submission appears exactly once")

      list.status                                                                    shouldBe 200
      s"<submissionId>$submissionId</submissionId>".r.findAllMatchIn(list.body).size shouldBe 1
    }

    Scenario("An amendment without a bearer token returns 401") {

      Given("an existing submission")

      val submissionId = createAcceptedSubmission()

      When("an amendment is submitted without authentication")

      val response =
        service.submitMessageWithoutAuth(Ie507PayloadBuilder.build(amendedFields(submissionId))).futureValue

      Then("the request is rejected as unauthorised")

      response.status shouldBe 401
      response.body   shouldBe empty
    }

    Scenario("An invalid amendment returns 400 and leaves the original submission unchanged") {

      Given("an existing submission")

      val submissionId = createAcceptedSubmission()

      When("an amendment with an invalid field value is submitted")

      val invalidXml =
        Ie507PayloadBuilder
          .build(amendedFields(submissionId))
          .replaceFirst("<discrepanciesExist>1</discrepanciesExist>", "<discrepanciesExist>X</discrepanciesExist>")

      val response = service.submitMessageWithCorrelationId(invalidXml, bearerToken, newCorrelationId()).futureValue

      Then("a bad request response is returned")

      response.status               shouldBe 400
      response.body                   should include("<errorResponse>")
      response.header("Content-Type") should contain("application/xml; charset=UTF-8")

      And("the original submission is unchanged")

      tagValue(service.getSubmission(submissionId, bearerToken).futureValue.body, "discrepanciesExist") shouldBe Some(
        "0"
      )
    }

    Scenario("A notification with an invalid token returns 401") {

      Given("a notification payload")

      val xml = NotificationPayloadBuilder.build(newCorrelationId(), Eori, Ie507PayloadBuilder.Mrn)

      When("it is sent with an invalid token")

      val response = service.submitNotification(xml, "invalid-token").futureValue

      Then("the request is rejected as unauthorised")

      response.status shouldBe 401
    }

    // Known issue: the backend saves by submissionId only, so another EORI can overwrite this submission.
    // Marked pendingUntilFixed until the backend is fixed (the bug ticket is AES-1093)
    Scenario("A trader cannot amend a submission belonging to another EORI") {

      Given("a submission belonging to the authenticated trader")

      val submissionId = createAcceptedSubmission()

      And("a bearer token for a different EORI")

      val otherToken =
        service
          .getBearerToken(
            AuthStubRequest(
              credId = "other-cred-id",
              enrolments = Seq(AuthStubEnrolment("HMRC-CUS-ORG", Seq(AuthStubIdentifier("EORINumber", "GB99999999"))))
            )
          )
          .futureValue

      When("the other trader submits an amendment for that submissionId")

      val response =
        service
          .submitMessageWithCorrelationId(
            Ie507PayloadBuilder.build(amendedFields(submissionId)),
            otherToken,
            newCorrelationId()
          )
          .futureValue

      Then("the amendment is rejected")

      response.status should (be >= 400 and be < 500)

      And("the original trader's submission is unchanged and still retrievable")

      val original = service.getSubmission(submissionId, bearerToken).futureValue
      original.status                               shouldBe 200
      tagValue(original.body, "discrepanciesExist") shouldBe Some("0")
    }

    // Blocked: the backend saves every message as Standard, not Amend,
    // so the status never becomes Amended (2).
    Scenario("An accepted amendment is shown with status Amended") {
      pending
    }
  }
}
