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

package views.view_application

import java.util.UUID

import audit.Auditable
import config.ApplicationConfig
import connectors.mock.MockAuthConnector
import controllers.BusinessDirectorsController
import javax.inject.Inject
import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.Save4LaterService
import services.mocks.MockSave4LaterService
import uk.gov.hmrc.play.test._

import scala.concurrent.Future
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import utils.AccountUtils

class ComponentTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach with MockAuthConnector with MockSave4LaterService {

  // implicit parameters required by the save4later calls, the actual values are not important as these calls are mocked
  implicit lazy val fakeRequest: FakeRequest[AnyContent] = {
    val sessionId = s"session-${UUID.randomUUID}"
    FakeRequest().withSession(
      SessionKeys.sessionId -> sessionId,
      SessionKeys.token -> "RANDOMTOKEN",
      SessionKeys.userId -> userId,
      "businessType" -> "SOP",
      "businessName" -> "North East Wines"
    )
  }

  class TestController @Inject()(override val mcc: MessagesControllerComponents,
                                 override val save4LaterService: Save4LaterService,
                                 override val authConnector: DefaultAuthConnector,
                                 override val auditable: Auditable,
                                 override val accountUtils: AccountUtils,
                                 override implicit val applicationConfig: ApplicationConfig) extends BusinessDirectorsController(mcc, save4LaterService, authConnector, auditable, accountUtils, applicationConfig) {

    def show(rowTitle: String, content: Option[String]*): Action[AnyContent] = Action.async {
      request =>
        val messages = mcc.messagesApi.preferred(request)
        setAuthMocks()
        restrictedAccessCheck {
          authorisedAction { ar =>
            // Run sbt test in terminal to compile tests and generate TableRowTestPage, otherwise it will show up red here
            Future.successful(Ok(views.html.view_application.TableRowTestPage(rowTitle, content: _*)(messages)))
          }(request, ec, hc, messages)
        }(request)
    }
  }

  val testController: TestController =
    new TestController(mockMCC, testSave4LaterService, mockAuthConnector, mockAuditable, mockAccountUtils, mockAppConfig){
      override val signInUrl = "/sign-in"
    }

  implicit def conv(str: String): Option[String] = Some(str)

  import scala.collection.JavaConversions._

  "row helper" should {

    case class Expectations(lines: String*)
    case class TestCase(data: Seq[Option[String]], expecations: Expectations)

    def testExpectations(result: Future[Result], expectations: Expectations) {
      val document = Jsoup.parse(contentAsString(result))
      val trs = document.getElementsByTag("tr")
      val trSize = trs.size()

      val expPSize = expectations.lines.size
      // the expected tr size is 1 if expSize > 0 or 0 otherwise
      val expTrSize =
        expectations.lines.size match {
          case 0 => return
          case _ => 1
        }
      trSize shouldBe expTrSize
      // each tr has exactly 2 tds
      // the first is the rowTitle which we are ignoring
      // the second is the contents specified with each element wrappined in a p tag
      val ps = trs.get(0).getElementsByTag("td").get(1).getElementsByTag("p")
      ps.size() shouldBe expPSize

      ps.map(x => x.text()) shouldBe expectations.lines
    }

    def runTests(testCases: TestCase*): Unit = {
      val rowTitle = "does not matter"
      testCases.foreach(testCase => testExpectations(testController.show(rowTitle, testCase.data: _*)(fakeRequest), testCase.expecations))
    }

    val line1 = "line 1"
    val line2 = "line 2"
    val line3 = "line 3"

    "When no data is provided then the row should not be displayed" in {
      val testCases = Seq(
        TestCase(Seq(), Expectations()),
        TestCase(Seq(None, None), Expectations())
      )
      runTests(testCases: _*)
    }

    "When all rows are presented then they should all be displayed" in {
      val testCases = Seq(
        TestCase(Seq(line1), Expectations(line1)),
        TestCase(Seq(line1, line2), Expectations(line1, line2))
      )
      runTests(testCases: _*)
    }

    "When not all rows are presented then only the defined row should all be displayed" in {
      val testCases = Seq(
        TestCase(Seq(line1, None, None), Expectations(line1)),
        TestCase(Seq(line1, None, line3), Expectations(line1, line3)),
        TestCase(Seq(None, None, line3), Expectations(line3))
      )
      runTests(testCases: _*)
    }
  }

}
