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

package services

import _root_.models._
import connectors.TaxEnrolmentsConnector
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.{Configuration, Play}
import play.api.Mode.Mode
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import utils.AwrsUnitTestTraits
import utils.TestConstants._
import uk.gov.hmrc.auth.core.retrieve.Retrievals._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}



class EnrolServiceTest extends AwrsUnitTestTraits {
  val userId = ""
  val saUtr: String = testUtr
  val ctUtr: String = testCTUtr
  val enrolRequestSAUTR =  EnrolRequest(portalId = "Default", serviceName = "HMRC-AWRS-ORG",
    friendlyName = "AWRS Enrolment", knownFacts = Seq("XAAW000000123456","",saUtr,"postcode"))
  val enrolRequestCTUTR =  EnrolRequest(portalId = "Default", serviceName = "HMRC-AWRS-ORG",
    friendlyName = "AWRS Enrolment", knownFacts = Seq("XAAW000000123456",ctUtr,"","postcode"))
  val enrolRequestNoSACT =  EnrolRequest(portalId = "Default", serviceName = "HMRC-AWRS-ORG",
    friendlyName = "AWRS Enrolment", knownFacts = Seq("XAAW000000123456","","","postcode"))
  val successfulEnrolResponse = Some(EnrolResponse(serviceName = "AWRS", state = "Not-activated",
    identifiers = List(Identifier("AWRS","Awrs-ref-no"))))
  val sourceId: String = "AWRS"
  val testBusinessCustomerDetails = BusinessCustomerDetails("ACME", Some("SOP"),
    BCAddress("line1", "line2", Option("line3"), Option("line4"), Option("post code"), Option("country")),
    "sap123", "safe123", false, Some("agent123"))
  val businessType = "LTD"
  val businessTypeSOP = "SOP"

  val successfulSubscriptionResponse = SuccessfulSubscriptionResponse("","XAAW000000123456","")
  val mockAuthConnector = mock[AuthConnector]
  val mockTaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]

  object EnrolServiceTest extends EnrolService {
    override val authConnector = mockAuthConnector
    override val taxEnrolmentsConnector = mockTaxEnrolmentsConnector

    override protected def mode: Mode = Play.current.mode

    override protected def runModeConfiguration: Configuration = Play.current.configuration
  }

  object EnrolServiceEMACTest extends EnrolService {
    override val authConnector = mockAuthConnector
    override val taxEnrolmentsConnector = mockTaxEnrolmentsConnector

    override protected def mode: Mode = Play.current.mode

    override protected def runModeConfiguration: Configuration = Play.current.configuration
  }

  override def beforeEach(): Unit = {
    reset(mockTaxEnrolmentsConnector)
  }

  "Enrol Service" should {
    behave like enrolService(EnrolServiceTest)
  }

  "Enrol Service with EMAC switched on" should {
    behave like enrolService(EnrolServiceEMACTest)
  }

  def mockAuthorise[T](predicate: Predicate, retrieval: Retrieval[T])(result: T): Unit =
    when(mockAuthConnector.authorise[T](
      Matchers.eq(predicate),
      Matchers.any[Retrieval[T]]
    )(Matchers.any[HeaderCarrier], Matchers.any[ExecutionContext]))
      .thenReturn(Future.successful(result))

  val testCredId = ""
  val testGroupId = ""

  def enrolService(enrolService: EnrolService): Unit = {
    "fetch data if found in save4later" in {
      mockAuthorise(EmptyPredicate, credentials and groupIdentifier)(new ~(Credentials(testCredId, EnrolService.GGProviderId), Some(testGroupId)))
      when(mockTaxEnrolmentsConnector.enrol(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(successfulEnrolResponse))
      val result = enrolService.enrolAWRS(successfulSubscriptionResponse,
        testBusinessCustomerDetails, businessType, Some(testUtr))
      await(result) shouldBe successfulEnrolResponse
    }

    "create correct EnrolRequest when business type is SOP and UTR present " in {
      val result = enrolService.createEnrolment(successfulSubscriptionResponse, testBusinessCustomerDetails, businessTypeSOP, saUtr)
      result shouldBe enrolRequestSAUTR
    }

    "create correct EnrolRequest when business type is other than SOP and UTR present " in {
      val result = enrolService.createEnrolment(successfulSubscriptionResponse, testBusinessCustomerDetails, "LTD", ctUtr)
      result shouldBe enrolRequestCTUTR
    }
    "create correct EnrolRequest when business type and UTR NOT present " in {
      val result = enrolService.createEnrolment(successfulSubscriptionResponse, testBusinessCustomerDetails,"" , None)
      result shouldBe enrolRequestNoSACT
    }
  }

}
