package com.jetsetter.pro.core.data.remote.fx

import com.jetsetter.pro.core.data.remote.ApiError
import com.jetsetter.pro.core.data.remote.ApiResult
import com.jetsetter.pro.core.data.remote.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live FX seam over [FrankfurterApi] — keyless (ECB reference rates), so no `isConfigured` gate
 * and no mock fallback here; callers (the currencytracker repository) hold the last-good cache
 * and the static-table fallback. Failures surface as the typed §2.1 [ApiResult] errors.
 */
@Singleton
class FrankfurterService @Inject constructor(
    private val api: FrankfurterApi,
) {

    /**
     * Latest reference rates quoted against [base] (e.g. "USD" → units of each currency one USD
     * buys). A structurally unusable payload (missing base/date/rates) maps to
     * [ApiError.DecodingFailed] rather than leaking a half-empty [FxRates].
     */
    suspend fun latest(base: String): ApiResult<FxRates> =
        when (val result = apiCall { api.latest(base = base) }) {
            is ApiResult.Success -> result.data.toFxRates()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(
                    ApiError.DecodingFailed(
                        IllegalStateException("Frankfurter payload missing base/date/rates"),
                    ),
                )
            is ApiResult.Failure -> result
        }
}

/**
 * A usable FX snapshot: [rates] = units of each currency code that one unit of [base] buys,
 * as of the ECB reference [date] (yyyy-MM-dd).
 */
data class FxRates(
    val base: String,
    val date: String,
    val rates: Map<String, Double>,
)

/**
 * Pure DTO→[FxRates] mapping (the FrankfurterMappingTest target). Null when the payload is
 * unusable: blank/missing base or date, or no positive finite rate surviving the filter —
 * non-positive and non-finite entries are dropped so a junk quote can never poison a conversion.
 */
fun FrankfurterLatestResponse.toFxRates(): FxRates? {
    val mappedBase = base?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
    val mappedDate = date?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val usable = rates.orEmpty().filterValues { it.isFinite() && it > 0.0 }
    if (usable.isEmpty()) return null
    return FxRates(base = mappedBase, date = mappedDate, rates = usable)
}
