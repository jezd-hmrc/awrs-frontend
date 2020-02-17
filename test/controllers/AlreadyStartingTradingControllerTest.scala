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
import forms.{AlreadyStartingTradingForm, BusinessDetailsForm}
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.DataCacheKeys._
import services.ServicesUnitTestFixture
import utils.TestUtil._
import utils.{AwrsUnitTestTraits, TestUtil}
import views.Configuration.NewApplicationMode

import scala.concurrent.Future

class AlreadyStartingTradingControllerTest extends AwrsUnitTestTraits
  with ServicesUnitTestFixture {

  val newBusinessName = "Changed"

  def testRequest(answer: String, entityType: String, hasAwrs: Boolean): FakeRequest[AnyContentAsFormUrlEncoded] =
    TestUtil.populateFakeRequest[String](FakeRequest(), AlreadyStartingTradingForm.alreadyStartedTradingForm, answer)

  val alreadyStartingTradingController: AlreadyStartingTradingController =
    new AlreadyStartingTradingController(mockMCC, testSave4LaterService, mockBusinessDetailsService, testKeyStoreService, mockAuthConnector, mockAuditable, mockAccountUtils, mockMainStoreSave4LaterConnector, mockAppConfig) {
    override val signInUrl = "/sign-in"
  }

  override def beforeEach(): Unit = {
    reset(mockAccountUtils)

    super.beforeEach()

  }

  def getExtendedBusinessDetails(updatedBusinessName: Boolean) : ExtendedBusinessDetails = {
    if (updatedBusinessName) {
      testExtendedBusinessDetails(businessName = newBusinessName)
    } else {
      testExtendedBusinessDetails()
    }
  }

  "showBusinessDetails" should {
    "show the business details page" when {
      "a user is logged in" in {
        val businessType = "test"

        setAuthMocks(mockAccountUtils = Some(mockAccountUtils))
        setupMockSave4LaterServiceWithOnly(
          fetchBusinessCustomerDetails = testBusinessCustomerDetails(businessType),
          fetchBusinessDetails = testBusinessDetails(),
          fetchNewApplicationType = testNewApplicationType
        )
        setupMockKeyStoreService(fetchAlreadyTrading = Some(true))
        when(mockMainStoreSave4LaterConnector.fetchData4Later[NewAWBusiness](ArgumentMatchers.any(), ArgumentMatchers.eq("tradingStartDetails"))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Option(NewAWBusiness("Yes", None))))

        when(mockBusinessDetailsService.businessDetailsPageRenderMode(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(NewApplicationMode))

        val res = alreadyStartingTradingController.showBusinessDetails(false)
          .apply(SessionBuilder.buildRequestWithSession(userId, businessType))

        status(res) shouldBe 200
      }
    }
  }

  "save" should {
    "save the already trading answer" when {
      "provided with an answer to the already trading question" in {
        val businessType = "test"
        val hasAwrs = true

        val fakeRequest = testRequest("Yes", businessType, hasAwrs)

        setAuthMocks(mockAccountUtils = Some(mockAccountUtils))
        setupMockSave4LaterServiceWithOnly(
          fetchBusinessCustomerDetails = testBusinessCustomerDetails(businessType),
          fetchBusinessDetails = testBusinessDetails(),
          fetchNewApplicationType = testNewApplicationType
        )
        setupMockKeyStoreService()
        when(mockMainStoreSave4LaterConnector.fetchData4Later[NewAWBusiness](ArgumentMatchers.any(), ArgumentMatchers.eq("tradingStartDetails"))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Option(NewAWBusiness("Yes", None))))

        when(mockBusinessDetailsService.businessDetailsPageRenderMode(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(NewApplicationMode))

        val res = alreadyStartingTradingController.saveAndReturn()
          .apply(SessionBuilder.updateRequestWithSession(fakeRequest, userId, businessType))

        status(res) shouldBe 303
        redirectLocation(res).get should include("/alcohol-wholesale-scheme/start-date-trading")
      }
    }
  }
}
