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

package forms

import forms.AWRSEnums.WithdrawalReasonEnum
import forms.AWRSEnums.WithdrawalReasonEnum._
import forms.test.util._
import forms.validation.util.FieldError
import models.WithdrawalReason
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.data.Form
import utils.AwrsFieldConfig

class WithdrawalReasonFormTest extends PlaySpec with MockitoSugar  with AwrsFieldConfig with AwrsFormTestUtils {

  // lazy required when AwrsFieldConfig is used to make sure the app is started before the config is accessed
  implicit lazy val testForm: Form[WithdrawalReason] = WithdrawalReasonForm.withdrawalReasonForm.form
  val preDefinedTypes: Set[AWRSEnums.WithdrawalReasonEnum.Value] = Set(AppliedInError, NoLongerTrading, DuplicateApplication, JoinedAWRSGroup)

  "Withdrawal Reason Form" must {
    "Correctly validate that at least one selection is made" in {
      val fieldId = "withdrawalReason"
      val fieldName = "reason"

      val emptyError = ExpectedFieldIsEmpty(fieldId, FieldError("awrs.withdrawal.error.reason_empty"))

      val expectations = CompulsoryEnumValidationExpectations(emptyError, WithdrawalReasonEnum)
      fieldName assertEnumFieldIsCompulsory expectations
    }
  }

  "check other details entered if 'Other' selected " must {
    val conditionPreDefinedSelected_theseShouldIgnoreTheValidationOnThisField: Set[Map[String, String]] =
      preDefinedTypes.foldLeft(Set[Map[String, String]]())((map, value) => map + Map("reason" -> value.toString))

    val conditionOtherSelected: Map[String, String] = Map("reason" -> Other.toString)

    "check 'Other' reason validation, when other is selected" in {
      val fieldId = "withdrawalReason-other"
      val fieldName = "reasonOther"

      val emptyError = ExpectedFieldIsEmpty(fieldId, FieldError("awrs.withdrawal.error.other.reason_empty"))
      val maxLenError = ExpectedFieldExceedsMaxLength(fieldId, "other reasons", withdrawalOtherReasonsLen)
      val invalidFormats = List(ExpectedInvalidFieldFormat("α", fieldId, "other reasons"))
      val formatError = ExpectedFieldFormat(invalidFormats)

      val expectations = CompulsoryFieldValidationExpectations(emptyError, maxLenError, formatError)

      fieldName assertFieldIsCompulsoryWhen(conditionOtherSelected, expectations)
      fieldName assertFieldIsIgnoredWhen(conditionPreDefinedSelected_theseShouldIgnoreTheValidationOnThisField, expectations.toFieldToIgnore)
    }
  }

}
