/*
 * Copyright 2019 HM Revenue & Customs
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
import controllers.auth.Utr._
import forms.BusinessDetailsForm
import models._
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.Mockito._
import services.DataCacheKeys._
import services.{KeyStoreService, Save4LaterService, ServicesUnitTestFixture}
import uk.gov.hmrc.auth.core.retrieve.{GGCredId, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment, EnrolmentIdentifier, Enrolments}
import utils.TestUtil._
import utils.{AwrsUnitTestTraits, TestUtil}

import scala.concurrent.Future

class BusinessDetailsControllerTest extends AwrsUnitTestTraits
  with ServicesUnitTestFixture {

  val newBusinessName = "Changed"

  def testRequest(extendedBusinessDetails: ExtendedBusinessDetails, entityType: String, hasAwrs: Boolean): FakeRequest[AnyContentAsFormUrlEncoded] =
    TestUtil.populateFakeRequest[ExtendedBusinessDetails](FakeRequest(), BusinessDetailsForm.businessDetailsValidationForm(entityType, hasAwrs), extendedBusinessDetails)

  val testBusinessDetailsController: BusinessDetailsController =
    new BusinessDetailsController(mockMCC, testSave4LaterService, testKeyStoreService, mockAuthConnector, mockAuditable, mockAccountUtils, mockMainStoreSave4LaterConnector, mockAppConfig) {
    override val signInUrl = "/sign-in"
  }

  override def beforeEach(): Unit = {
    reset(mockAccountUtils)

    super.beforeEach()
  }

  "BusinessDetailsController" must {

    "Users who entered from the summary edit view" should {
      allEntities.foreach {
        businessType =>
          Seq(true, false).foreach {
            hasAwrs =>
              Seq(true, false).foreach {
                updatedBusinessName =>
                  s"go to the correct view after clicking return when business type=$businessType and hasAwrs=$hasAwrs and updatedBusinessName=$updatedBusinessName" in {
                    returnWithAuthorisedUser(businessType, hasAwrs, updatedBusinessName, testRequest(getExtendedBusinessDetails(updatedBusinessName), businessType, hasAwrs)) {
                      result =>
                        (hasAwrs, updatedBusinessName, businessType) match {
                          case (true, true, "LLP_GRP" | "LTD_GRP") => redirectLocation(result).get should include("/alcohol-wholesale-scheme/business-details/group-representative")
                          case _ =>
                            redirectLocation(result).get should include(f"/alcohol-wholesale-scheme/view-section/$businessDetailsName")
                            verifySave4LaterService(saveBusinessDetails = 1)
                        }
                    }
                  }
              }
          }
      }
    }

    def getExtendedBusinessDetails(updatedBusinessName: Boolean) : ExtendedBusinessDetails = {
      updatedBusinessName match {
        case true => testExtendedBusinessDetails(businessName = newBusinessName)
        case false => testExtendedBusinessDetails()
      }
    }

    def returnWithAuthorisedUser(businessType: String, hasAwrs: Boolean, updatedBusinessName: Boolean, fakeRequest: FakeRequest[AnyContentAsFormUrlEncoded])(test: Future[Result] => Any) {
      setUser(hasAwrs = hasAwrs)
      setupMockSave4LaterServiceWithOnly(
        fetchBusinessCustomerDetails = testBusinessCustomerDetails(businessType),
        fetchBusinessDetails = testBusinessDetails(),
        fetchNewApplicationType = testNewApplicationType
      )
      if (updatedBusinessName) setupMockKeyStoreServiceWithOnly(fetchExtendedBusinessDetails = testExtendedBusinessDetails(businessName = newBusinessName))
      if (hasAwrs) setAuthMocks(mockAccountUtils = Some(mockAccountUtils)) else setAuthMocks(Future.successful(new ~( new ~(Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("utr", "0123456")), "activated"))), Some(AffinityGroup.Organisation)), GGCredId("fakeCredID"))))

      val result = testBusinessDetailsController.saveAndReturn().apply(SessionBuilder.updateRequestWithSession(fakeRequest, userId, businessType))
      test(result)
    }

  }
}
