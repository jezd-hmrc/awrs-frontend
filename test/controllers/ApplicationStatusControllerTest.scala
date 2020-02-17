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
import models.FormBundleStatus.Pending
import models._
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.i18n.Messages
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.ServicesUnitTestFixture
import utils.{AwrsNumberFormatter, AwrsUnitTestTraits, TestUtil}

import scala.concurrent.Future
import scala.util.Try



class ApplicationStatusControllerTest extends AwrsUnitTestTraits
  with ServicesUnitTestFixture {

  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  lazy val testBusinessType: BusinessType = BusinessType(legalEntity = Option("LTD_GRP"), isSaAccount = None, isOrgAccount = None)
  lazy val testBusinessCustomerDetails: BusinessCustomerDetails = TestUtil.testBusinessCustomerDetails("LLP")
  lazy val testPendingSubscriptionStatusType: SubscriptionStatusType = TestUtil.testSubscriptionStatusTypePending
  lazy val testApprovedSubscriptionStatusType: SubscriptionStatusType = TestUtil.testSubscriptionStatusTypeApproved
  lazy val testApprovedWithConditionsSubscriptionStatusType: SubscriptionStatusType = TestUtil.testSubscriptionStatusTypeApprovedWithConditions
  lazy val testApprovedWithConditionsStatusInfoType: StatusInfoType = TestUtil.testStatusInfoTypeApprovedWithConditions
  lazy val testRejectedSubscriptionStatusType: SubscriptionStatusType = TestUtil.testSubscriptionStatusTypeRejected
  lazy val testRejectedReviewSubscriptionStatusType: SubscriptionStatusType = TestUtil.testSubscriptionStatusTypeRejectedUnderReviewOrAppeal
  lazy val testRevokedSubscriptionStatusType: SubscriptionStatusType = TestUtil.testSubscriptionStatusTypeRevoked
  lazy val testRevokedReviewSubscriptionStatusType: SubscriptionStatusType = TestUtil.testSubscriptionStatusTypeRevokedUnderReviewOrAppeal
  lazy val testRejectedStatusInfoType: StatusInfoType = TestUtil.testStatusInfoTypeRejected
  lazy val testRejectedReviewStatusInfoType: StatusInfoType = TestUtil.testStatusInfoTypeRejectedUnderReviewOrAppeal
  lazy val testRevokedStatusInfoType: StatusInfoType = TestUtil.testStatusInfoTypeRevoked
  lazy val testRevokedReviewStatusInfoType: StatusInfoType = TestUtil.testStatusInfoTypeRevokedUnderReviewOrAppeal

  lazy val testStatusNotificationMindedToReject: Option[StatusNotification] = TestUtil.testStatusNotificationMindedToReject
  lazy val testStatusInfoTypeMindedToReject: StatusInfoType = TestUtil.testStatusInfoTypeMindedToReject
  lazy val testStatusInfoTypeNoLongerMindedToReject: StatusInfoType = TestUtil.testStatusInfoTypeNoLongerMindedToReject
  lazy val testStatusNotificationMindedToRevoke: Option[StatusNotification] = TestUtil.testStatusNotificationMindedToRevoke
  lazy val testStatusNotificationNoLongerMindedToRevoke: Option[StatusNotification] = TestUtil.testStatusNotificationNoLongerMindedToRevoke
  lazy val testStatusInfoTypeMindedToRevoke: StatusInfoType = TestUtil.testStatusInfoTypeMindedToRevoke
  lazy val testStatusInfoTypeNoLongerMindedToRevoke: StatusInfoType = TestUtil.testStatusInfoTypeNoLongerMindedToRevoke
  lazy val testBusinessDetails: tickBox => BusinessDetails = {
    case true => TestUtil.testBusinessDetails(newBusiness = TestUtil.testIsNewBusiness)
    case false => TestUtil.testBusinessDetails()
  }

  val testApplicationStatusController = new ApplicationStatusController(mockMCC, testStatusManagementService, mockAuditable, mockAccountUtils, mockAuthConnector, testSave4LaterService, mockAppConfig)

  "ApplicationStatusController" should {
    def testPageIsDisplayed(result: Future[Result]) = status(result) shouldBe OK

    def testPageIsSkipped(result: Future[Result]) = status(result) shouldBe SEE_OTHER

    "Display the status page on an inital visit when routed from home controller or the link" in {
      returningTestUser(initialVisit = true, FromHomeController)(testPageIsDisplayed)
      returningTestUser(initialVisit = true, FromStatusPageLink)(testPageIsDisplayed)
    }

    "Display the status page on subsequent visits only when routed from the link" in {
      returningTestUser(initialVisit = false, FromHomeController)(testPageIsSkipped)
      returningTestUser(initialVisit = false, FromStatusPageLink)(testPageIsDisplayed)
    }
  }

  "When status page needs to be displayed the ApplicationStatusController" should {
    "redirect to pending status page if API 9 Status is pending and API 12 Notification is not minded to reject" in {
      lazy val verifyHeadingAndReturnDocument = (result: Future[Result]) => {
        status(result) shouldBe OK
        val document = Jsoup.parse(contentAsString(result))
        checkStatusPageExitPoints(document, testPendingSubscriptionStatusType.formBundleStatus.name)
        val lede = StandardLedeParam(
          testBusinessCustomerDetails.businessName,
          Messages("awrs.application_status.lede.verb_in_main.pending"),
          testPendingSubscriptionStatusType.processingDate,
          None)
        checkLedeIsCorrect(document, lede)
        val pendingProgressBarParam =
          ProgressBarTestParam(Indicator(HasTickBox, progressBarFullClass),
            Indicator(NoTickBox, progressBarFullClass),
            Indicator(NoTickBox, progressBarEmptyClass))
        checkStatusProgressBarIsCorrect(document, pendingProgressBarParam)
        document
      }: Document

      getWithPendingUser(isNewBusiness = true, {
        result => verifyHeadingAndReturnDocument(result)
      })
      beforeEach()
      getWithPendingUser(isNewBusiness = false, {
        result => verifyHeadingAndReturnDocument(result)
      })
    }

    "redirect to minded to reject status page if API 9 Status is pending and API 12 Notification is minded to reject" in {
      getWithMindedToRejectUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkStatusPageExitPoints(document, testPendingSubscriptionStatusType.formBundleStatus.name)
          val lede = AlertLedeParam(
            testBusinessCustomerDetails.businessName,
            Messages("awrs.application_status.alert_lede.verb_in_main.mindful_to_reject"),
            None)
          checkLedeIsCorrect(document, lede)
          val pendingProgressBarParam =
            ProgressBarTestParam(Indicator(HasTickBox, progressBarFullClass),
              Indicator(NoTickBox, progressBarFullClass),
              Indicator(NoTickBox, progressBarEmptyClass))
          checkStatusProgressBarIsCorrect(document, pendingProgressBarParam)
          statusInfoMessageIsDisplayed(document, testStatusInfoTypeMindedToReject.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }

    "redirect to approved status page if API 9 Status is approved" in {
      getWithApprovedUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkStatusPageExitPoints(document, testApprovedSubscriptionStatusType.formBundleStatus.name)
              val lede = StandardLedeParam(
                testBusinessCustomerDetails.businessName,
                Messages("awrs.application_status.lede.verb_in_main.approved"),
                testApprovedSubscriptionStatusType.processingDate,
                Some("0123456"))
              checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
      }
    }

    "redirect to minded to reject status page if API 9 Status is approved and API 12 Notification is minded to revoke" in {
      getWithApprovedMindedToRevokeUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkStatusPageExitPoints(document, testPendingSubscriptionStatusType.formBundleStatus.name)
              val lede = AlertLedeParam(
                testBusinessCustomerDetails.businessName,
                Messages("awrs.application_status.alert_lede.verb_in_main.mindful_to_revoke"),
                Some("0123456"))
              checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
          statusInfoMessageIsDisplayed(document, testStatusInfoTypeMindedToRevoke.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }

    "redirect to no longer minded to reject status page if API 9 Status is approved and API 12 Notification is no longer minded to revoke" in {
      getWithApprovedNoLongerMindedToRevokeUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkStatusPageExitPoints(document, testApprovedSubscriptionStatusType.formBundleStatus.name)
              val lede = AlertLedeParam(
                testBusinessCustomerDetails.businessName,
                Messages("awrs.application_status.alert_lede.verb_in_main.no_longer_mindful_to_revoke"),
                Some("0123456"))
              checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
          statusInfoMessageIsDisplayed(document, testStatusInfoTypeNoLongerMindedToRevoke.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }

    "redirect to approved with conditions status page if API 9 Status is approved with conditions" in {
      getWithApprovedWithConditionsUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkStatusPageExitPoints(document, testApprovedWithConditionsSubscriptionStatusType.formBundleStatus.name)
              val lede = StandardLedeParam(
                testBusinessCustomerDetails.businessName,
                Messages("awrs.application_status.lede.verb_in_main.approved_with_conditions"),
                testApprovedWithConditionsSubscriptionStatusType.processingDate,
                Some("0123456"))
              checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
          statusInfoMessageIsDisplayed(document, testApprovedWithConditionsStatusInfoType.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }

    "redirect to minded to reject status page if API 9 Status is approved with conditions and API 12 Notification is minded to revoke" in {
      getWithApprovedWithConditionsMindedToRevokeUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkStatusPageExitPoints(document, testApprovedWithConditionsSubscriptionStatusType.formBundleStatus.name)
              val lede = AlertLedeParam(
                testBusinessCustomerDetails.businessName,
                Messages("awrs.application_status.alert_lede.verb_in_main.mindful_to_revoke"),
                Some("0123456"))
              checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
          statusInfoMessageIsDisplayed(document, testStatusInfoTypeMindedToRevoke.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }

    "redirect to no longer minded to reject status page if API 9 Status is approved with conditions and API 12 Notification is no longer minded to revoke" in {
      getWithApprovedWithConditionsNoLongerMindedToRevokeUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkStatusPageExitPoints(document, testApprovedWithConditionsSubscriptionStatusType.formBundleStatus.name)
              val lede = AlertLedeParam(
                testBusinessCustomerDetails.businessName,
                Messages("awrs.application_status.alert_lede.verb_in_main.no_longer_mindful_to_revoke"),
                Some("0123456"))
              checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
          statusInfoMessageIsDisplayed(document, testStatusInfoTypeNoLongerMindedToRevoke.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }

    "redirect to rejected status page if API 9 Status is rejected" in {
      getWithRejectedUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkViewApplicationPointsToViewApplication(document)

          val lede = StandardLedeParam(
            testBusinessCustomerDetails.businessName,
            Messages("awrs.application_status.lede.verb_in_main.rejected"),
            testRejectedSubscriptionStatusType.processingDate,
            None)
          checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
          statusInfoMessageIsDisplayed(document, testRejectedStatusInfoType.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }

    "redirect to rejected under review status page if API 9 Status is rejected under review" in {
      getWithRejectedReviewUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkViewApplicationPointsToViewApplication(document)

          val lede = UnderAppealOrReviewLedeParam(
            testBusinessCustomerDetails.businessName,
            Messages("awrs.application_status.lede.verb_in_main.rejected"),
            Messages("awrs.application_status.under_review_or_appeal_lede.noun_in_main.rejected")
          )
          checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
          statusInfoMessageIsDisplayed(document, testRejectedReviewStatusInfoType.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }

    "redirect to revoked under review status page if API 9 Status is revoked under review" in {
      getWithRevokedReviewUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkViewApplicationPointsToViewApplication(document)

          val lede = UnderAppealOrReviewLedeParam(
            testBusinessCustomerDetails.businessName,
            Messages("awrs.application_status.lede.verb_in_main.revoked"),
            Messages("awrs.application_status.under_review_or_appeal_lede.noun_in_main.revoked")

          )
          checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
          statusInfoMessageIsDisplayed(document, testRevokedReviewStatusInfoType.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }

    "redirect to revoked status page if API 9 Status is revoked" in {
      getWithRevokedUser {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          checkViewApplicationPointsToViewApplication(document)

          val lede = StandardLedeParam(
            testBusinessCustomerDetails.businessName,
            Messages("awrs.application_status.lede.verb_in_main.revoked"),
            testRevokedSubscriptionStatusType.processingDate,
            None)
          checkLedeIsCorrect(document, lede)
          checkStatusProgressBarIsNotOnPage(document)
          statusInfoMessageIsDisplayed(document, testRevokedStatusInfoType.response.get.asInstanceOf[StatusInfoSuccessResponseType].secureCommText)
      }
    }
  }

  type tickBox = Boolean
  val tickBoxClass = "tick"

  val HasTickBox: tickBox = true

  val NoTickBox: tickBox = false

  type barClass = String

  def progressBarFullClass: barClass = "appstatus-complete"

  def progressBarEmptyClass: barClass = "appstatus-processing"

  case class Indicator(tickBox: tickBox, fillBar: barClass)

  case class ProgressBarTestParam(received: Indicator, processed: Indicator, status: Indicator)

  sealed trait LedgeParam {
    def companyName: String

    def verb: String

    def awrsRegeNo: Option[String]
  }

  case class StandardLedeParam(companyName: String, verb: String, date: String, awrsRegeNo: Option[String]) extends LedgeParam

  case class UnderAppealOrReviewLedeParam(companyName: String, verb: String, noun: String, awrsRegeNo: Option[String] = None) extends LedgeParam

  case class AlertLedeParam(companyName: String, verb: String, awrsRegeNo: Option[String]) extends LedgeParam

  def checkLedeIsCorrect(document: Document, expected: LedgeParam): Unit = {
    val lede = document.select("p.lede")

    val coreLede = expected match {
      case param: StandardLedeParam =>
        Messages("awrs.application_status.lede.main", param.companyName, param.verb, param.date)
      case param: UnderAppealOrReviewLedeParam =>
        Messages("awrs.application_status.under_review_or_appeal_lede.main.line1",
          param.verb,
          param.noun,
          param.companyName,
          Messages("awrs.application_status.lede.verb_in_main.under_review_or_appeal")
        ) + " " + Messages("awrs.application_status.under_review_or_appeal_lede.main.line2")
      case param: AlertLedeParam =>
        Messages("awrs.application_status.alert_lede.main", param.verb, param.companyName)
    }

    expected.awrsRegeNo match {
      case None => lede.text shouldBe coreLede
      case Some(awrsRegN) =>
        val awrsRegText: String = Messages("awrs.application_status.awrsRegNo", AwrsNumberFormatter.format(awrsRegN))
        lede.text shouldBe f"$coreLede $awrsRegText"
    }
  }

  def checkStatusProgressBarIsNotOnPage(document: Document): Unit = {
    val received = document.getElementById("indication-received")
    val processed = document.getElementById("indication-processed")
    val status = document.getElementById("indication-approved")

    received shouldBe null
    processed shouldBe null
    status shouldBe null
  }

  def checkStatusProgressBarIsCorrect(document: Document, expected: ProgressBarTestParam): Unit = {
    val received = document.getElementById("indication-received")
    val processed = document.getElementById("indication-processed")
    val status = document.getElementById("indication-approved")

    def assertCorrectTickBox(element: Element, expected: tickBox) {
      expected match {
        case HasTickBox | true => element.attr("class") should include(tickBoxClass)
        case NoTickBox | false => element.attr("class") should not include tickBoxClass
      }
    }

    assertCorrectTickBox(received, expected.received.tickBox)
    received.attr("class") should include(expected.received.fillBar)

    assertCorrectTickBox(processed, expected.processed.tickBox)
    processed.attr("class") should include(expected.processed.fillBar)

    assertCorrectTickBox(status, expected.status.tickBox)
    status.attr("class") should include(expected.status.fillBar)
  }

  def checkStatusPageExitPoints(document: Document, status: String): Unit =
    status match {
      case "Rejected" | "Revoked" | "RejectedUnderReviewOrAppeal" | "RevokedUnderReviewOrAppeal " =>
        document.select("a.link").attr("href") shouldBe controllers.routes.ApplicationStatusController.showStatus(true).url
        document.select("a.button").attr("href") shouldBe controllers.routes.IndexController.showIndex().url
      case _ =>
        document.getElementById("sign-in-to-view").attr("href") shouldBe controllers.routes.IndexController.showIndex().url
    }

  def checkViewApplicationPointsToViewApplication(document: Document): Unit =
    document.select("a.button").attr("href") shouldBe controllers.routes.ViewApplicationController.show(false).url

  def checkViewOrEditApplicationDoesNotExist(document: Document): Unit =
    document.select("a.button").attr("href").length shouldBe 0

  def statusInfoMessageIsDisplayed(document: Document, expected: String): Unit =
    document.select(".form-group").first().text() should include(expected)

  def statusTestUser(status: SubscriptionStatusType,
                     statusInfo: MockConfiguration[Option[StatusInfoType]] = DoNotConfigure,
                     notification: Option[StatusNotification] = None,
                     isNewBusiness: Boolean = false
                    )(test: Future[Result] => Any) = {
    setUser(hasAwrs = true)
    setupMockSave4LaterServiceWithOnly(
      fetchBusinessType = testBusinessType,
      fetchBusinessCustomerDetails = testBusinessCustomerDetails,
      fetchBusinessDetails = testBusinessDetails(isNewBusiness)
    )
    when(mockMainStoreSave4LaterConnector.fetchData4Later[NewAWBusiness](ArgumentMatchers.any(), ArgumentMatchers.eq("tradingStartDetails"))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(Option(NewAWBusiness("Yes", None))))
    setupMockTestStatusManagementService(
      status = status.formBundleStatus,
      statusInfo = statusInfo,
      notification = notification match {
        case None => None
        case Some(alert) => alert.contactType
      },
      configuration = MockStatusManagementServiceConfiguration(
        api9 = CachedLocally,
        api11 = CachedLocally,
        api12Cache = CachedLocally
      )
    )
    setAuthMocks()

    val result = testApplicationStatusController.showStatus(printFriendly = false, mustShow = true).apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  sealed trait StatusPageVisitRule

  case object FromHomeController extends StatusPageVisitRule

  case object FromStatusPageLink extends StatusPageVisitRule

  /*
  *  This test is used to test the first vist and return user behaviour introduced by AWRS-1659
  *  where the status page is displayed on a user's initial visit would not be automatically displayed on a
  *  user's subsequent visit
  */
  def returningTestUser(initialVisit: Boolean,
                        visitFrom: StatusPageVisitRule)(test: Future[Result] => Any): Unit = {
    setUser(hasAwrs = true)
    setupMockSave4LaterServiceWithOnly(
      fetchBusinessType = testBusinessType,
      fetchBusinessCustomerDetails = testBusinessCustomerDetails
    )
    when(mockMainStoreSave4LaterConnector.fetchData4Later[NewAWBusiness](ArgumentMatchers.any(), ArgumentMatchers.eq("tradingStartDetails"))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(Option(NewAWBusiness("Yes", None))))
    setupMockTestStatusManagementService(
      status = Pending,
      statusInfo = None,
      notification = None,
      configuration = MockStatusManagementServiceConfiguration(
        api9 = CachedLocally,
        api11 = CachedLocally,
        api12Cache = CachedLocally,
        statusPageViewed = !initialVisit
      )
    )
    setAuthMocks(mockAccountUtils = Some(mockAccountUtils))

    val result = testApplicationStatusController.showStatus(
      printFriendly = false,
      mustShow = visitFrom match {
        case FromHomeController => false
        case FromStatusPageLink => true
      }
    ).apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  def getWithPendingUser(isNewBusiness: Boolean, test: Future[Result] => Any) = statusTestUser(testPendingSubscriptionStatusType, isNewBusiness = isNewBusiness)(test)

  def getWithApprovedUser(test: Future[Result] => Any) = statusTestUser(testApprovedSubscriptionStatusType)(test)

  def getWithApprovedWithConditionsUser(test: Future[Result] => Any) = statusTestUser(testApprovedWithConditionsSubscriptionStatusType, Some(testApprovedWithConditionsStatusInfoType))(test)

  def getWithRejectedUser(test: Future[Result] => Any) = statusTestUser(testRejectedSubscriptionStatusType, Some(testRejectedStatusInfoType))(test)

  def getWithRevokedUser(test: Future[Result] => Any) = statusTestUser(testRevokedSubscriptionStatusType, Some(testRevokedStatusInfoType))(test)

  def getWithRejectedReviewUser(test: Future[Result] => Any) = statusTestUser(testRejectedReviewSubscriptionStatusType, Some(testRejectedReviewStatusInfoType))(test)

  def getWithRevokedReviewUser(test: Future[Result] => Any) = statusTestUser(testRevokedReviewSubscriptionStatusType, Some(testRevokedReviewStatusInfoType))(test)

  def getWithMindedToRejectUser(test: Future[Result] => Any) = statusTestUser(testPendingSubscriptionStatusType, notification = testStatusNotificationMindedToReject)(test)

  def getWithApprovedMindedToRevokeUser(test: Future[Result] => Any) = statusTestUser(testApprovedSubscriptionStatusType, notification = testStatusNotificationMindedToRevoke)(test)

  def getWithApprovedNoLongerMindedToRevokeUser(test: Future[Result] => Any) = statusTestUser(testApprovedSubscriptionStatusType, notification = testStatusNotificationNoLongerMindedToRevoke)(test)

  def getWithApprovedWithConditionsMindedToRevokeUser(test: Future[Result] => Any) = statusTestUser(testApprovedWithConditionsSubscriptionStatusType, notification = testStatusNotificationMindedToRevoke)(test)

  def getWithApprovedWithConditionsNoLongerMindedToRevokeUser(test: Future[Result] => Any) = statusTestUser(testApprovedWithConditionsSubscriptionStatusType, notification = testStatusNotificationNoLongerMindedToRevoke)(test)

}
