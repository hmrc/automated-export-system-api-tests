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

package uk.gov.hmrc.api.services

import uk.gov.hmrc.api.models.AuthStubRequest
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ServiceFactory @Inject() (
  client: HttpClientV2
)(implicit ec: ExecutionContext) {

  private val authService =
    new AuthService(client)

  private val aesService =
    new AesService(client)

  def getBearerToken =
    authService.getBearerToken()

  def getBearerToken(
    request: AuthStubRequest
  ) =
    authService.getBearerToken(request)

  def submitMessage(
    xml: String,
    bearerToken: String
  ) =
    aesService.submitMessage(
      xml,
      bearerToken
    )

  def submitMessageWithoutAuth(
    xml: String
  ) =
    aesService.submitMessageWithoutAuth(xml)

  def getSubmissions(
    bearerToken: String
  ) =
    aesService.getSubmissions(
      bearerToken
    )

  def getSubmission(
    submissionId: String,
    bearerToken: String
  ) =
    aesService.getSubmission(
      submissionId,
      bearerToken
    )
}
