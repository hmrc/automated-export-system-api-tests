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

import play.api.libs.ws.writeableOf_String
import uk.gov.hmrc.api.conf.TestEnvironment
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpResponse}

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

class AesService(client: HttpClientV2)(implicit ec: ExecutionContext) {

  private val baseUrl =
    TestEnvironment.url("aes")

  private val aesUrl =
    s"$baseUrl/message"

  def submitMessage(
    xml: String,
    bearerToken: String
  ): Future[HttpResponse] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrier(
        authorization = Some(Authorization(s"Bearer $bearerToken"))
      )

    client
      .post(URI.create(aesUrl).toURL)
      .setHeader(
        "Content-Type" ->
          "application/xml"
      )
      .withBody(xml)
      .execute[HttpResponse]
  }

  def submitMessageWithoutAuth(
    xml: String
  ): Future[HttpResponse] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrier()

    client
      .post(URI.create(aesUrl).toURL)
      .setHeader(
        "Content-Type" ->
          "application/xml"
      )
      .withBody(xml)
      .execute[HttpResponse]
  }

  def getSubmissions(
    bearerToken: String
  ): Future[HttpResponse] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrier(
        authorization = Some(Authorization(s"Bearer $bearerToken"))
      )

    client
      .get(URI.create(s"$baseUrl/submissions").toURL)
      .execute[HttpResponse]
  }

  def getSubmission(
    submissionId: String,
    bearerToken: String
  ): Future[HttpResponse] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrier(
        authorization = Some(Authorization(s"Bearer $bearerToken"))
      )

    client
      .get(URI.create(s"$baseUrl/submission/$submissionId").toURL)
      .execute[HttpResponse]
  }
}
