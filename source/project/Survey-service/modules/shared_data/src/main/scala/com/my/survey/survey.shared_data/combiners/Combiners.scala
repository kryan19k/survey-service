package com.my.survey.shared_data.combiners

import com.my.survey.shared_data.serializers.Serializers
import com.my.survey.shared_data.types._
import monocle.Monocle.toAppliedFocusOps
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.schema.address.Address
import org.tessellation.security.hash.Hash

object Combiners {
  def combineCreateSurvey(
    update: CreateSurvey,
    state: DataState[SurveyState, SurveyCalculatedState],
    creator: Address
  ): DataState[SurveyState, SurveyCalculatedState] = {
    val surveyId = Hash.fromBytes(Serializers.serializeUpdate(update)).toString
    val newSurvey = Survey(surveyId, creator, update.survey.questions, update.survey.tokenReward, update.survey.imageUri, System.currentTimeMillis(), update.survey.publicKey)

    val newUpdatesList = state.onChain.updates :+ update
    val newCalculatedState = state.calculated.focus(_.surveys).modify(_.updated(surveyId, newSurvey))

    DataState(SurveyState(newUpdatesList), newCalculatedState)
  }

  def combineSubmitResponse(
    update: SubmitResponse,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataState[SurveyState, SurveyCalculatedState] = {
    val newResponse = SurveyResponse(update.response.surveyId, update.response.respondent, update.response.encryptedAnswers, update.response.earnedReward, System.currentTimeMillis())

    val newUpdatesList = state.onChain.updates :+ update
    val newCalculatedState = state.calculated.focus(_.responses).modify { responses =>
      val updatedResponses = responses.getOrElse(update.response.surveyId, List.empty) :+ newResponse
      responses.updated(update.response.surveyId, updatedResponses)
    }

    DataState(SurveyState(newUpdatesList), newCalculatedState)
  }
}