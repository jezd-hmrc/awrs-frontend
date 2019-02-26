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

package models

import play.api.libs.json.{JsValue, JsResult, Reads, Json}

case class NewApplicationType(isNewApplication: Option[Boolean])

object NewApplicationType {
  val reader = new Reads[NewApplicationType] {
    def reads(js: JsValue): JsResult[NewApplicationType] = {
      for{
        isNewApplications <- (js \ "isNewApplication").validateOpt[Boolean]
      } yield {
        NewApplicationType(isNewApplications)
      }
    }
  }

  implicit val formats = Json.format[NewApplicationType]
}
