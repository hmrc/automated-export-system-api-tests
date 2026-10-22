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

import org.scalatest.Tag
import uk.gov.hmrc.api.models.{AuthStubEnrolment, AuthStubIdentifier, AuthStubRequest}

import scala.util.Random

/** Tag for the async notification / diversion / error mapping tests, so they can be run on their own. */
object AsyncNotificationTag extends Tag("AsyncNotifications")

/** Each scenario uses its own EORI so its submissions never mix with other specs (which share GB12345678). */
object TestEori {

  def random(): String =
    "GB" + Seq.fill(12)(Random.nextInt(10)).mkString

  def authRequest(eori: String): AuthStubRequest =
    AuthStubRequest(
      credId = s"cred-$eori",
      enrolments = Seq(
        AuthStubEnrolment(
          key = "HMRC-CUS-ORG",
          identifiers = Seq(
            AuthStubIdentifier(
              key = "EORINumber",
              value = eori
            )
          )
        )
      )
    )
}

/** Fields the stubs react to (automated-export-system-stubs: SyncErrorPolicy, AckRule, IE906Rules, IE917Rules). */
final case class AsyncTriggerFields(
  submissionId: String,
  mrn: String = Ie507PayloadBuilder.Mrn,
  officeOfExit: String = "GB000051",
  parentUcrId: Option[String] = None,
  numberOfSeals: Option[Int] = None,
  additionalIdentifier: Option[String] = None,
  grossMass: Option[String] = None,
  netMass: Option[String] = None
)

/** Builds an IE507 payload from valid-ie507.xml with the optional trigger fields added in schema order. */
object AsyncPayloadBuilder {

  def build(fields: AsyncTriggerFields): String = {
    val base =
      Ie507PayloadBuilder.build(
        Ie507Fields(
          submissionId = fields.submissionId,
          officeOfExit = fields.officeOfExit
        )
      )

    val parentUcrIdXml =
      fields.parentUcrId
        .map(value => s"<parentUCRID>$value</parentUCRID>")
        .getOrElse("")

    val transportEquipmentXml =
      fields.numberOfSeals
        .map(seals =>
          s"<TransportEquipment>" +
            s"<sequenceNumber>1</sequenceNumber>" +
            s"<containerIdentificationNumber>CONT0000001</containerIdentificationNumber>" +
            s"<numberOfSeals>$seals</numberOfSeals>" +
            s"</TransportEquipment>"
        )
        .getOrElse("")

    val withMrn =
      base.replaceFirst("<MRN>[^<]*</MRN>", s"<MRN>${fields.mrn}</MRN>")

    // Consignment order: referenceNumberUCR, parentUCRID, TransportEquipment, LocationOfGoods
    val withConsignmentFields =
      withMrn.replaceFirst(
        "</referenceNumberUCR>",
        s"</referenceNumberUCR>$parentUcrIdXml$transportEquipmentXml"
      )

    // LocationOfGoods order: typeOfLocation, qualifierOfIdentification, authorisationNumber, additionalIdentifier
    val withAdditionalIdentifier =
      fields.additionalIdentifier.fold(withConsignmentFields)(value =>
        withConsignmentFields.replaceFirst(
          "</authorisationNumber>",
          s"</authorisationNumber><additionalIdentifier>$value</additionalIdentifier>"
        )
      )

    val withGrossMass =
      fields.grossMass.fold(withAdditionalIdentifier)(value =>
        withAdditionalIdentifier.replaceFirst("<grossMass>[^<]*</grossMass>", s"<grossMass>$value</grossMass>")
      )

    fields.netMass.fold(withGrossMass)(value =>
      withGrossMass.replaceFirst("<netMass>[^<]*</netMass>", s"<netMass>$value</netMass>")
    )
  }
}

final case class NotificationErrorFields(
  code: String,
  description: String,
  path: String,
  originalValue: String
)

/** Same as NotificationPayloadBuilder, plus an <errors> block in the format the backend /notification reads. */
object NotificationWithErrorsPayloadBuilder {

  def build(
    correlationId: String,
    eori: String,
    mrn: String,
    status: Int,
    errors: Seq[NotificationErrorFields]
  ): String = {
    val errorsXml =
      errors.map { error =>
        s"<error>" +
          s"<code>${error.code}</code>" +
          s"<description>${error.description}</description>" +
          s"<path>${error.path}</path>" +
          s"<originalValue>${error.originalValue}</originalValue>" +
          s"</error>"
      }.mkString

    NotificationPayloadBuilder
      .build(correlationId, eori, mrn, status)
      .replace("</notification>", s"  <errors>$errorsXml</errors>\n</notification>")
  }
}

/** Regex based readers for the GET /submissions and GET /submission/:id XML responses. */
object SubmissionXml {

  final case class ReturnedError(
    code: Option[String],
    description: Option[String],
    path: Option[String]
  )

  def tagValue(body: String, tag: String): Option[String] =
    s"(?s)<$tag>(.*?)</$tag>".r.findFirstMatchIn(body).map(_.group(1).trim)

  /** The <Submission> block for one submissionId inside a GET /submissions response. */
  def summaryFor(submissionsBody: String, submissionId: String): Option[String] =
    "(?s)<Submission>(.*?)</Submission>".r
      .findAllMatchIn(submissionsBody)
      .map(_.group(1))
      .find(_.contains(s"<submissionId>$submissionId</submissionId>"))

  /** All <error> blocks (from <metadata>) in a GET /submission/:id response. */
  def errors(submissionBody: String): Seq[ReturnedError] =
    "(?s)<error>(.*?)</error>".r
      .findAllMatchIn(submissionBody)
      .map(_.group(1))
      .map(error =>
        ReturnedError(
          code = tagValue(error, "code"),
          description = tagValue(error, "description"),
          path = tagValue(error, "path")
        )
      )
      .toSeq
}
