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

import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.ExecutionContext

import uk.gov.hmrc.api.helpers.Module
import uk.gov.hmrc.api.services.ServiceFactory
import uk.gov.hmrc.http.client.HttpClientV2

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

// AES-856 testOnly endpoint to delete all data in the test database. This is used to ensure that each test run starts with a clean slate. It is called once before any tests are run.
private object TestDataCleanup {

  lazy val runOnce: Unit = {
    val cleanupHttpClient = HttpClient.newHttpClient()
    val request           = HttpRequest
      .newBuilder()
      .uri(URI.create("http://localhost:5000/automated-export-system/test-only/delete-all"))
      .GET()
      .build()
    cleanupHttpClient.send(request, HttpResponse.BodyHandlers.discarding())
    ()
  }

}

trait BaseSpec
    extends AnyFeatureSpec
    with GivenWhenThen
    with Matchers
    with Eventually
    with ScalaFutures
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite {

  TestDataCleanup.runOnce

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = Span(30, Seconds),
      interval = Span(500, Millis)
    )

  implicit lazy val ec: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  override lazy val app: Application =
    new GuiceApplicationBuilder()
      .bindings(new Module)
      .build()

  protected lazy val httpClient: HttpClientV2 =
    app.injector.instanceOf[HttpClientV2]

  protected lazy val service: ServiceFactory =
    new ServiceFactory(httpClient)
}
