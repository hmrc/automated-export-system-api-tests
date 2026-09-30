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

import java.util.UUID

object TransportDocumentXmlBuilder {

  final case class TransportDocumentEntry(
    sequenceNumber: Int,
    documentType: Int,
    referenceNumber: String
  )

  final case class Submission(submissionId: String, xml: String)

  private lazy val template = PayloadLoader.load("transportdocument-template.xml")

  private def toXml(entry: TransportDocumentEntry): String =
    s"""
       |<TransportDocument>
       |  <sequenceNumber>${entry.sequenceNumber}</sequenceNumber>
       |  <type>${entry.documentType}</type>
       |  <referenceNumber>${entry.referenceNumber}</referenceNumber>
       |</TransportDocument>
       |""".stripMargin

  def withEntries(
    entries: Seq[TransportDocumentEntry],
    submissionId: String = UUID.randomUUID().toString
  ): Submission = {

    val transportDocumentBlocks =
      entries.map(toXml).mkString("\n")

    Submission(
      submissionId,
      template
        .replace("{{SUBMISSION_ID}}", submissionId)
        .replace("{{TRANSPORT_DOCUMENT_BLOCKS}}", transportDocumentBlocks)
    )
  }

  def withCount(
    count: Int,
    submissionId: String = UUID.randomUUID().toString
  ): Submission =
    withEntries(
      (1 to count).map { n =>
        TransportDocumentEntry(
          sequenceNumber = n,
          documentType = 1,
          referenceNumber = s"REF-$n"
        )
      },
      submissionId
    )

}
