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

package utils

import audit.Auditable
import controllers.auth.StandardAuthRetrievals
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.play.test.UnitSpec

class AccountUtilsTest extends UnitSpec with MockitoSugar{

  val accountUtils = new AccountUtils(mock[Auditable])

  "getUtr" should {
    "Return the user SA utr " in {
      val utr = accountUtils.getUtr(TestUtil.authRetrievalSAUTR)
      utr shouldBe "0123456"
    }

    "Return the user CT utr for org" in {
      val utr = accountUtils.getUtr(TestUtil.defaultAuthRetrieval)
      utr shouldBe "6543210"
    }

    "throw exception if no details are found" in {
      val thrown = the[RuntimeException] thrownBy accountUtils.getUtr(TestUtil.emptyAuthRetrieval)
      thrown.getMessage should include("[getUtr] No UTR found")
    }
  }

  "getAuthType " should {
    "return the correct auth link based on a sole trader legal entity type" in {
      val result = accountUtils.getAuthType("SOP", TestUtil.defaultAuthRetrieval)
      result shouldBe s"sa/0123456"
    }
    "return the correct auth link based on a partnership legal entity type" in {
      val result = accountUtils.getAuthType("Partnership", TestUtil.defaultAuthRetrieval)
      result shouldBe "org/UNUSED"
    }
    "return the correct auth link based on a limited company legal entity type" in {
      val result = accountUtils.getAuthType("LTD", TestUtil.defaultAuthRetrieval)
      result shouldBe "org/UNUSED"
    }
    "throw an exception if Partnership legal entity enrolment and not an organisation" in {
      val thrown = the[RuntimeException] thrownBy  accountUtils.getAuthType("Partnership", StandardAuthRetrievals(TestUtil.defaultEnrolmentSet, Some(AffinityGroup.Individual), "fakeCredId"))
      thrown.getMessage shouldBe "[getAuthType] Not an organisation account"
    }
  }
}
