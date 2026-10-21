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

import uk.gov.hmrc.api.conf.TestEnvironment
import uk.gov.hmrc.api.helpers.*
import uk.gov.hmrc.http.HttpResponse

import java.util.UUID

class AsyncNotificationStatusSpec extends BaseSpec {

  // <status> values returned by GET /submissions and GET /submission/:id (backend NotificationEventStatus)
  private val AwaitingStatus = "0"
  private val AcceptedStatus = "1"
  private val RejectedStatus = "4"

  // <status> values accepted by the backend /notification endpoint (backend NotificationStatus)
  private val NotificationAccepted  = 1
  private val NotificationRejected  = 2
  private val NotificationDiversion = 5

  // Stubs SyncErrorPolicy: MRN ending A3 -> EIS returns 400, so no async notification is ever sent
  private val SyncRejectedMrn = "26GB0000X6524786A3"

  // Stubs IE906Rules: MRN ending B0 -> IE906 functional error code 90 on Body.ExportOperation.MRN
  private val Ie906Mrn = "26GB0000X6524786B0"

  // Stubs AckRule: parentUCRID ending BB -> ACK Diversion (action code 5) followed by a second response
  private val DiversionParentUcrId = "GB/000000000000-123BB"

  private final case class TestUser(eori: String, token: String)

  private def newUser(): TestUser = {
    val eori = TestEori.random()
    TestUser(eori, service.getBearerToken(TestEori.authRequest(eori)).futureValue)
  }

  private def newCorrelationId(): String =
    UUID.randomUUID().toString.replace("-", "")

  private def submit(
    user: TestUser,
    fields: AsyncTriggerFields,
    correlationId: String = newCorrelationId()
  ): HttpResponse =
    service
      .submitMessageWithCorrelationId(AsyncPayloadBuilder.build(fields), user.token, correlationId)
      .futureValue

  private def statusInList(user: TestUser, submissionId: String): Option[String] = {
    val response = service.getSubmissions(user.token).futureValue
    response.status shouldBe 200
    SubmissionXml
      .summaryFor(response.body, submissionId)
      .flatMap(SubmissionXml.tagValue(_, "status"))
  }

  private def awaitStatusInList(user: TestUser, submissionId: String, expected: String): Unit =
    eventually {
      statusInList(user, submissionId) shouldBe Some(expected)
    }

  private def submissionBody(user: TestUser, submissionId: String): String = {
    val response = service.getSubmission(submissionId, user.token).futureValue
    response.status shouldBe 200
    response.body
  }

  /** A submission EIS rejects synchronously: it is saved as Awaiting and the stubs never send a notification. */
  private def createSubmissionWithoutAsyncResponse(user: TestUser): (String, String) = {
    val submissionId  = UUID.randomUUID().toString
    val correlationId = newCorrelationId()

    val response = submit(user, AsyncTriggerFields(submissionId, mrn = SyncRejectedMrn), correlationId)
    response.status should be >= 400

    (submissionId, correlationId)
  }

  /** Sends a notification straight to the backend /notification endpoint (bypasses stubs and notifications). */
  private def sendNotification(
    user: TestUser,
    correlationId: String,
    status: Int,
    errors: Seq[NotificationErrorFields] = Seq.empty
  ): HttpResponse = {
    val xml =
      if (errors.isEmpty) NotificationPayloadBuilder.build(correlationId, user.eori, SyncRejectedMrn, status)
      else NotificationWithErrorsPayloadBuilder.build(correlationId, user.eori, SyncRejectedMrn, status, errors)

    service.submitNotification(xml, TestEnvironment.notificationToken).futureValue
  }

  Feature("GET submissions returns the status of the most recent notification event (end to end via stubs)") {

    Scenario("A submission with no async response has status AWAITING", AsyncNotificationTag) {

      Given("a submission that EIS rejects synchronously, so no async response will ever arrive")

      val user              = newUser()
      val (submissionId, _) = createSubmissionWithoutAsyncResponse(user)

      When("the submissions endpoint is called")

      Then("the submission is listed with status AWAITING (0)")

      statusInList(user, submissionId) shouldBe Some(AwaitingStatus)

      And("it stays AWAITING because no notification is received")

      (1 to 5).foreach { _ =>
        Thread.sleep(1000)
        statusInList(user, submissionId) shouldBe Some(AwaitingStatus)
      }

      And("the single submission has status AWAITING and no errors")

      val body = submissionBody(user, submissionId)
      SubmissionXml.tagValue(body, "status") shouldBe Some(AwaitingStatus)
      SubmissionXml.errors(body) shouldBe empty
    }

    Scenario("A standard submission accepted by the stubs has status ACCEPTED", AsyncNotificationTag) {

      Given("a valid standard submission")

      val user         = newUser()
      val submissionId = UUID.randomUUID().toString

      When("it is submitted")

      submit(user, AsyncTriggerFields(submissionId)).status shouldBe 202

      Then("the stubs send an ACK and the submission is listed with status ACCEPTED (1)")

      awaitStatusInList(user, submissionId, AcceptedStatus)
    }

    Scenario("A diversion followed by an ACK returns the ACK as the latest status", AsyncNotificationTag) {

      Given("a submission whose parentUCRID ends with BB")

      val user         = newUser()
      val submissionId = UUID.randomUUID().toString

      When("it is submitted")

      submit(user, AsyncTriggerFields(submissionId, parentUcrId = Some(DiversionParentUcrId))).status shouldBe 202

      Then("the stubs send a diversion then an ACK, and the submission is listed with status ACCEPTED (1)")

      awaitStatusInList(user, submissionId, AcceptedStatus)
    }

    Scenario("A diversion followed by an IE906 returns the IE906 as the latest status", AsyncNotificationTag) {

      Given("a submission whose parentUCRID ends with BB and whose MRN ends with B0")

      val user         = newUser()
      val submissionId = UUID.randomUUID().toString

      When("it is submitted")

      submit(
        user,
        AsyncTriggerFields(submissionId, mrn = Ie906Mrn, parentUcrId = Some(DiversionParentUcrId))
      ).status shouldBe 202

      Then("the stubs send a diversion then an IE906, and the submission is listed with status REJECTED (4)")

      awaitStatusInList(user, submissionId, RejectedStatus)

      And("the single submission returns the IE906 error for the MRN")

      val errors = SubmissionXml.errors(submissionBody(user, submissionId))
      errors                    should have size 1
      errors.head.path          shouldBe Some("Body.ExportOperation.MRN")
      errors.head.code          should not be Some("UNKNOWN_ERROR")
    }

    Scenario("A diversion followed by an IE917 returns the IE917 as the latest status", AsyncNotificationTag) {

      Given("a submission whose parentUCRID ends with BB and whose transport equipment has 40 seals")

      val user         = newUser()
      val submissionId = UUID.randomUUID().toString

      When("it is submitted")

      submit(
        user,
        AsyncTriggerFields(submissionId, parentUcrId = Some(DiversionParentUcrId), numberOfSeals = Some(40))
      ).status shouldBe 202

      Then("the stubs send a diversion then an IE917, and the submission is listed with status REJECTED (4)")

      awaitStatusInList(user, submissionId, RejectedStatus)

      And("the single submission returns the mapped IE917 error")

      SubmissionXml.errors(submissionBody(user, submissionId)) shouldBe Seq(
        SubmissionXml.ReturnedError(
          code = Some("NOT_SUPPORTED_IN_POSITION"),
          description = Some("The field or value is not valid in this part of the message."),
          path = Some("Body.GoodsShipment.Consignment.TransportEquipment.numberOfSeals")
        )
      )
    }
  }

  Feature("Diversion handling (notifications sent directly to the backend)") {

    Scenario("A diversion on its own keeps the submission AWAITING", AsyncNotificationTag) {

      Given("a submission with no async response")

      val user                          = newUser()
      val (submissionId, correlationId) = createSubmissionWithoutAsyncResponse(user)

      When("a diversion notification is received")

      sendNotification(user, correlationId, NotificationDiversion).status shouldBe 204

      Then("the submission is still listed with status AWAITING (0)")

      statusInList(user, submissionId) shouldBe Some(AwaitingStatus)
    }

    Scenario("A diversion followed by an ACK returns only the most recent event", AsyncNotificationTag) {

      Given("a submission that has received a diversion")

      val user                          = newUser()
      val (submissionId, correlationId) = createSubmissionWithoutAsyncResponse(user)

      sendNotification(user, correlationId, NotificationDiversion).status shouldBe 204

      When("an accepted notification is received for the same correlationId")

      sendNotification(user, correlationId, NotificationAccepted).status shouldBe 204

      Then("the submission is listed with the status of the most recent event, ACCEPTED (1)")

      statusInList(user, submissionId) shouldBe Some(AcceptedStatus)

      And("the single submission has status ACCEPTED and no errors")

      val body = submissionBody(user, submissionId)
      SubmissionXml.tagValue(body, "status") shouldBe Some(AcceptedStatus)
      SubmissionXml.errors(body) shouldBe empty
    }

    Scenario("A diversion followed by a rejection returns the rejection and its errors", AsyncNotificationTag) {

      Given("a submission that has received a diversion")

      val user                          = newUser()
      val (submissionId, correlationId) = createSubmissionWithoutAsyncResponse(user)

      sendNotification(user, correlationId, NotificationDiversion).status shouldBe 204

      When("a rejected notification with an error is received for the same correlationId")

      val error =
        NotificationErrorFields(
          code = "TOO_MANY_REPETITIONS",
          description = "Too many occurrences of the same element were provided.",
          path = "Body.GoodsShipment.Consignment.parentUCRID",
          originalValue = "GB/000000000000-12322"
        )

      sendNotification(user, correlationId, NotificationRejected, Seq(error)).status shouldBe 204

      Then("the submission is listed with status REJECTED (4)")

      statusInList(user, submissionId) shouldBe Some(RejectedStatus)

      And("the single submission returns the error of the most recent event")

      SubmissionXml.errors(submissionBody(user, submissionId)) shouldBe Seq(
        SubmissionXml.ReturnedError(
          code = Some(error.code),
          description = Some(error.description),
          path = Some(error.path)
        )
      )
    }

    Scenario("A notification for a correlationId that is already resolved returns 404", AsyncNotificationTag) {

      Given("a submission that has received a diversion followed by an ACK")

      val user                          = newUser()
      val (submissionId, correlationId) = createSubmissionWithoutAsyncResponse(user)

      sendNotification(user, correlationId, NotificationDiversion).status shouldBe 204
      sendNotification(user, correlationId, NotificationAccepted).status shouldBe 204

      When("another notification is received for the same correlationId")

      val response = sendNotification(user, correlationId, NotificationRejected)

      Then("a not found response is returned")

      response.status shouldBe 404

      And("the submission keeps status ACCEPTED (1)")

      statusInList(user, submissionId) shouldBe Some(AcceptedStatus)
    }

    Scenario("Each submission in the list reflects its own most recent event", AsyncNotificationTag) {

      Given("two submissions for the same EORI")

      val user                                  = newUser()
      val (resolvedSubmissionId, correlationId) = createSubmissionWithoutAsyncResponse(user)
      val (awaitingSubmissionId, _)             = createSubmissionWithoutAsyncResponse(user)

      When("only the first receives a diversion followed by an ACK")

      sendNotification(user, correlationId, NotificationDiversion).status shouldBe 204
      sendNotification(user, correlationId, NotificationAccepted).status shouldBe 204

      Then("the first is listed as ACCEPTED (1) and the second as AWAITING (0)")

      statusInList(user, resolvedSubmissionId) shouldBe Some(AcceptedStatus)
      statusInList(user, awaitingSubmissionId) shouldBe Some(AwaitingStatus)
    }
  }
}
