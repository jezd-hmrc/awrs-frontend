/*
 * Copyright 2020 HM Revenue & Customs
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

package models

import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec


class DeRegistrationTest extends UnitSpec with MockitoSugar {
  "DeRegistration.toDeRegistration" should {
    "transform the date into the correct format" in {
      val day = "31"
      val month = "03"
      val year = "2017"
      val date: DeRegistrationDate = DeRegistrationDate(TupleDate(day, month, year))
      val reason: DeRegistrationReason = DeRegistrationReason(Some("no reason"), None)

      DeRegistration.toDeRegistration(Some(date), None) shouldBe None
      DeRegistration.toDeRegistration(None, Some(reason)) shouldBe None

      val toMiddle = DeRegistration.toDeRegistration(Some(date), Some(reason))
      toMiddle.get.deregistrationDate shouldBe f"$year-$month-$day"
    }
  }
}
