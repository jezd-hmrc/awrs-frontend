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

package connectors

import audit.Auditable
import javax.inject.Inject
import metrics.AwrsMetrics
import models.{RequestPayload, _}
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import services.GGConstants._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import utils.LoggingUtils
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw

import scala.concurrent.{ExecutionContext, Future}

class TaxEnrolmentsConnector @Inject()(servicesConfig: ServicesConfig,
                                       http: DefaultHttpClient,
                                       metrics: AwrsMetrics,
                                       val auditable: Auditable) extends LoggingUtils {
  val serviceURL: String = servicesConfig.baseUrl("tax-enrolments")
  val AWRS_SERVICE_NAME = "HMRC-AWRS-ORG"
  val EnrolmentIdentifierName = "AWRSRefNumber"
  val retryLimit = 7
  val retryWait = 1000 // milliseconds

  val deEnrolURI = "tax-enrolments/de-enrol"
  val enrolmentUrl = s"$serviceURL/tax-enrolments"

  val emptyResponse: EnrolResponse = EnrolResponse("", "", Seq.empty)

  def enrol(requestPayload: RequestPayload,
            groupId: String,
            awrsRegistrationNumber: String,
            businessPartnerDetails: BusinessCustomerDetails,
            businessType: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[EnrolResponse]] = {
    val timer = metrics.startTimer(ApiType.API4Enrolment)
    val enrolmentKey = s"$AWRS_SERVICE_NAME~$EnrolmentIdentifierName~$awrsRegistrationNumber"
    val postUrl = s"""$enrolmentUrl/groups/$groupId/enrolments/$enrolmentKey"""
    val auditMap: Map[String, String] = Map(
      "safeId" -> businessPartnerDetails.safeId,
      "UserDetail" -> businessPartnerDetails.businessName,
      "legal-entity" -> businessType)
    val response = trySend(0, postUrl, requestPayload, auditMap).map(_ => Option(emptyResponse))
    timer.stop()
    response
  }

  def trySend(tries: Int, postUrl: String,
              requestPayload: RequestPayload,
              auditMap: Map[String, String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val jsonData: JsValue = Json.toJson(requestPayload)
    http.POST[JsValue, HttpResponse](postUrl, jsonData).flatMap {
      response =>
        Future.successful(processResponse(response, postUrl, requestPayload))
    }.recoverWith {
      case e =>
        if (tries < retryLimit) {
          Future {
            warn(s"Retrying EMAC Enrol - call number: $tries. - ${e.getMessage}")
            Thread.sleep(retryWait)
          }.flatMap(_ => trySend(tries + 1, postUrl, requestPayload, auditMap))
        }
        else {
          warn(s"Retrying EMAC Enrol - retry limit exceeded.")
          audit(transactionName = auditEMACTxName,
            detail = auditMap ++ Map("verifiers" -> requestPayload.verifiers.toString, "Exception" -> e.getMessage),
            eventType = eventTypeFailure)
          // Code changed to hide the Enrol failure from the user.
          // The failure will need to be sorted out manually and there is nothing the user can do at the time.
          // The manual process will take place after the Enrol failure is picked up in Splunk.
          Future.successful(HttpResponse.apply(OK, ""))
        }
    }
  }

  def processResponse(response: HttpResponse, postUrl: String, requestPayload: RequestPayload): HttpResponse = {
    response.status match {
      case OK =>
        metrics.incrementSuccessCounter(ApiType.API4Enrolment)
        response
      case CREATED =>
        metrics.incrementSuccessCounter(ApiType.API4Enrolment)
        response
      case BAD_REQUEST =>
        metrics.incrementFailedCounter(ApiType.API4Enrolment)
        logger.warn(s"[TaxEnrolmentsConnector][enrol] - " +
          s"enrolment url:$postUrl, " +
          s"Bad Request Exception account Ref:${requestPayload.verifiers}, " +
          s"Service: $AWRS_SERVICE_NAME")
        throw new BadRequestException(response.body)
      case NOT_FOUND =>
        metrics.incrementFailedCounter(ApiType.API4Enrolment)
        logger.warn(s"[TaxEnrolmentsConnector][enrol] - " +
          s"Not Found Exception account Ref:${requestPayload.verifiers}, " +
          s"Service: $AWRS_SERVICE_NAME}")
        throw new NotFoundException(response.body)
      case SERVICE_UNAVAILABLE =>
        metrics.incrementFailedCounter(ApiType.API4Enrolment)
        logger.warn(s"[TaxEnrolmentsConnector][enrol] - " +
          s"enrolment url:$postUrl, " +
          s"Service Unavailable Exception account Ref:${requestPayload.verifiers}, " +
          s"Service: $AWRS_SERVICE_NAME}")
        throw new ServiceUnavailableException(response.body)
      case BAD_GATEWAY =>
        metrics.incrementFailedCounter(ApiType.API4Enrolment)
        createWarning(postUrl, None, requestPayload.verifiers, response.body, response.status, Some("BAD_GATEWAY"))
        response
      case status =>
        metrics.incrementFailedCounter(ApiType.API4Enrolment)
        createWarning(postUrl, Some(status), requestPayload.verifiers, response.body, response.status)
        throw new InternalServerException(response.body)
    }
  }

  private def createWarning(postUrl: String,
                            optionStatus: Option[Int],
                            verifiers: List[Verifier],
                            responseBody: String,
                            responseStatus: Int,
                            optionErrorStatus: Option[String] = None): Unit = {
    val errorStatus = optionErrorStatus.fold("")(status => " - " + status)
    logger.warn(s"[TaxEnrolmentsConnector][enrol]$errorStatus" +
      s"enrolment url:$postUrl, " +
      optionStatus.map(status => s"status:$status Exception account Ref:$verifiers, ") +
      s"Service: $AWRS_SERVICE_NAME" +
      s"Reponse Body: $responseBody," +
      s"Reponse Status: $responseStatus")
  }

  def deEnrol(awrsRef: String, businessName: String, businessType: String)
             (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val timer = metrics.startTimer(ApiType.API10DeEnrolment)
    val jsonData = Json.toJson(DeEnrolRequest(keepAgentAllocations))

    val auditMap: Map[String, String] = Map("UserDetail" -> businessName, "legal-entity" -> businessType)
    val auditSubscribeTxName: String = "AWRS ETMP de-enrol"

    val postUrl = s"""$serviceURL/$deEnrolURI/$service"""
    http.POST[JsValue, HttpResponse](postUrl, jsonData) map {
      response =>
        timer.stop()
        response.status match {
          case OK => warn(s"[TaxEnrolmentsConnector][deEnrol] - Ok")
            metrics.incrementSuccessCounter(ApiType.API10DeEnrolment)
            audit(transactionName = auditSubscribeTxName, detail = auditMap, eventType = eventTypeSuccess)
            true
          case status =>
            warn(s"[TaxEnrolmentsConnector][API10 De-Enrolment - $businessName, $awrsRef, $status ] - ${response.body} ")
            metrics.incrementFailedCounter(ApiType.API10DeEnrolment)
            audit(transactionName = auditSubscribeTxName, detail = auditMap ++ Map("Business Name" -> businessName,
              "AWRS Ref" -> awrsRef,
              "Status" -> status.toString,
              "FailureReason" -> response.body), eventType = eventTypeFailure)
            false
        }
    }
  }
}