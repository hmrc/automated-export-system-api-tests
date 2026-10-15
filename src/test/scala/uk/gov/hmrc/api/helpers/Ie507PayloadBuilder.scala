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

package uk.gov.hmrc.api.helpers

import java.time.{LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

final case class Ie507Fields(
  submissionId: String,
  officeOfExit: String = "GB000051",
  discrepanciesExist: Int = 0,
  splitIndicator: Int = 0,
  shippingMarks: String = "MARKS123"
)

object Ie507PayloadBuilder {

  // MRN used in valid-ie507.xml
  val Mrn: String = "26GB0000X6524786A9"

  private lazy val template: String =
    PayloadLoader.load("valid-ie507.xml")

  def build(fields: Ie507Fields): String =
    template
      .replaceFirst("<submissionId>[^<]*</submissionId>", s"<submissionId>${fields.submissionId}</submissionId>")
      .replaceFirst(
        "<referenceNumber>[^<]*</referenceNumber>",
        s"<referenceNumber>${fields.officeOfExit}</referenceNumber>"
      )
      .replaceFirst(
        "<discrepanciesExist>[^<]*</discrepanciesExist>",
        s"<discrepanciesExist>${fields.discrepanciesExist}</discrepanciesExist>"
      )
      .replaceFirst(
        "<splitIndicator>[^<]*</splitIndicator>",
        s"<splitIndicator>${fields.splitIndicator}</splitIndicator>"
      )
      .replaceFirst("<shippingMarks>[^<]*</shippingMarks>", s"<shippingMarks>${fields.shippingMarks}</shippingMarks>")
}

object NotificationPayloadBuilder {

  private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

  // status: 1 = Accepted, 2 = Rejected, 5 = Diversion
  def build(correlationId: String, eori: String, mrn: String, status: Int = 1): String =
    s"""<notification>
       |  <correlationId>$correlationId</correlationId>
       |  <eori>$eori</eori>
       |  <mrn>$mrn</mrn>
       |  <dateCreated>${LocalDateTime.now(ZoneOffset.UTC).format(dateFormat)}</dateCreated>
       |  <status>$status</status>
       |</notification>""".stripMargin
}
