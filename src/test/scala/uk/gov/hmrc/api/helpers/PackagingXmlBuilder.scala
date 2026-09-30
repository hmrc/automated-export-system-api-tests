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

object PackagingXmlBuilder {

  final case class PackagingEntry(
    sequenceNumber: Int,
    typeOfPackages: String = "PA",
    numberOfPackages: Int = 10,
    shippingMarks: Option[String] = None
  )

  final case class Submission(submissionId: String, xml: String)

  private lazy val template: String =
    PayloadLoader.load("packaging-template.xml")

  private def toXml(entry: PackagingEntry): String =
    s"""<Packaging>
       |    <sequenceNumber>${entry.sequenceNumber}</sequenceNumber>
       |    <typeOfPackages>${entry.typeOfPackages}</typeOfPackages>
       |    <numberOfPackages>${entry.numberOfPackages}</numberOfPackages>
       |    <shippingMarks>${entry.shippingMarks.getOrElse(s"MARKS-${entry.sequenceNumber}")}</shippingMarks>
       |</Packaging>""".stripMargin

  def withEntries(
    entries: Seq[PackagingEntry],
    submissionId: String = UUID.randomUUID().toString
  ): Submission =
    Submission(
      submissionId,
      template
        .replace("{{SUBMISSION_ID}}", submissionId)
        .replace("{{PACKAGING_BLOCKS}}", entries.map(toXml).mkString("\n\n"))
    )

  def withCount(
    count: Int,
    submissionId: String = UUID.randomUUID().toString
  ): Submission =
    withEntries((1 to count).map(n => PackagingEntry(sequenceNumber = n)), submissionId)
}
