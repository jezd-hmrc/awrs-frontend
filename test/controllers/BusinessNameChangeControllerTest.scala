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

package controllers

import builders.SessionBuilder
import connectors.mock.MockAuthConnector
import models._
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.mocks.{MockIndexService, MockKeyStoreService, MockSave4LaterService}
import utils.AwrsUnitTestTraits
import utils.TestUtil._

import scala.concurrent.Future

class BusinessNameChangeControllerTest extends AwrsUnitTestTraits
  with MockAuthConnector
  with MockSave4LaterService
  with MockKeyStoreService
  with MockIndexService {

  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  val newBusinessName = "Changed"

  val testBusinessNameChange = BusinessNameChangeConfirmation("Yes")

  val testBusinessNameChangeController: BusinessNameChangeController =
    new BusinessNameChangeController(mockMCC, testKeyStoreService, testSave4LaterService, mockIndexService, mockAuthConnector, mockAuditable, mockAccountUtils, mockAppConfig) {
    override val signInUrl: String = applicationConfig.signIn
  }

  "Submitting the business name change confirmation form with " should {
    "Authenticated and authorised users" should {
      "redirect to view section for Business Details page when valid data is provided" in {
        continueWithAuthorisedUser(FakeRequest().withFormUrlEncodedBody("businessNameChangeConfirmation" -> "Yes")) {
          result =>
            redirectLocation(result).get should include("/alcohol-wholesale-scheme/view-section/businessDetails")
        }
      }
      "save form data to Save4Later and redirect to Index page " in {
        continueWithAuthorisedUser(FakeRequest().withFormUrlEncodedBody("businessNameChangeConfirmation" -> "Yes")) {
          result =>
            status(result) should be(SEE_OTHER)
            verifySave4LaterService(saveBusinessCustomerDetails = 1)
        }
      }
    }
  }

  private def continueWithAuthorisedUser(fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded])(test: Future[Result] => Any) {
    setupMockKeyStoreServiceWithOnly(fetchExtendedBusinessDetails = testExtendedBusinessDetails(businessName = newBusinessName))
    setupMockSave4LaterService()
    setAuthMocks()
    val result = testBusinessNameChangeController.callToAction().apply(SessionBuilder.updateRequestWithSession(fakeRequest, userId))
    test(result)
  }

}
