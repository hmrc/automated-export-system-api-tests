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
import uk.gov.hmrc.api.models.{AuthStubEnrolment, AuthStubIdentifier, AuthStubRequest}

import java.util.UUID

class GetSubmissionSpec extends BaseSpec with BeforeAndAfterAll {

  private var bearerToken: String  = _
  private var submissionId: String = _

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

    val submissionsResponse =
      service
        .getSubmissions(bearerToken)
        .futureValue

    submissionId = "<submissionId>(.*?)</submissionId>".r
      .findFirstMatchIn(submissionsResponse.body)
      .map(_.group(1))
      .getOrElse(
        throw new RuntimeException(
          "No submissionId found in submissions list - cannot proceed with test setup"
        )
      )
  }

  Feature("Get Submission by SubmissionId") {

    Scenario("Authenticated user can retrieve a submission using a valid submissionId") {

      Given("a valid bearer token and a submissionId belonging to the authenticated user")

      When("the submission endpoint is called with that submissionId")

      val response =
        service
          .getSubmission(submissionId, bearerToken)
          .futureValue

      Then("a successful response is returned")

      response.status shouldBe 200

      And("the response contains the submission root element")

      response.body should include("<Submission>")

      And("the response contains the matching submissionId")

      response.body should include(s"<submissionId>$submissionId</submissionId>")
    }

    Scenario("Retrieving a submission with a non-existent submissionId returns 404") {

      Given("a valid bearer token")

      And("a submissionId that does not exist")

      val nonExistentId =
        UUID.randomUUID().toString

      When("the submission endpoint is called with that submissionId")

      val response =
        service
          .getSubmission(nonExistentId, bearerToken)
          .futureValue

      Then("a not found response is returned")

      response.status shouldBe 404
    }

    Scenario("Users cannot retrieve submissions belonging to another EORI") {

      Given("a bearer token for a different EORI")

      val otherUserToken =
        service
          .getBearerToken(
            AuthStubRequest(
              enrolments = Seq(
                AuthStubEnrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(
                    AuthStubIdentifier(
                      key = "EORINumber",
                      value = "GB99999999"
                    )
                  )
                )
              )
            )
          )
          .futureValue

      When("that user attempts to retrieve a submission belonging to another EORI")

      val response =
        service
          .getSubmission(submissionId, otherUserToken)
          .futureValue

      Then("a not found response is returned, without revealing the submission exists")

      response.status shouldBe 404
    }
  }
}
