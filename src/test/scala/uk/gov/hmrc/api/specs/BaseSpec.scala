/*
 * Copyright 2024 HM Revenue & Customs
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
import org.scalatest.concurrent.Eventually
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

trait BaseSpec extends AnyFeatureSpec with GivenWhenThen with Matchers with Eventually with BeforeAndAfterEach {

  // This configuration determines how long `eventually` will wait for its assertions to become true
  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = Span(30, Seconds),
      interval = Span(500, Millis)
    )

  override protected def beforeEach(): Unit =
    super.beforeEach()

  override protected def afterEach(): Unit =
    super.afterEach()
}
