package com.jetsetter.pro.core.ai

import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.Trip
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The tools IRIS can call to actually drive the app (Anthropic `tool_use`). [schema] is the
 * tool-definition array sent on every request; [execute] runs a tool the model asked for against
 * the real repositories and returns a short result string that goes back to the model as the
 * `tool_result`, which it then narrates to the user.
 *
 * Tools only touch on-device repositories (Room, offline-first); nothing here sends user data to a
 * third party beyond what the model already sees in the conversation.
 */
@Singleton
class IrisToolDispatcher @Inject constructor(
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
) {
    suspend fun execute(name: String, input: JSONObject): String = when (name) {
        "add_trip" -> addTrip(input)
        "log_expense" -> logExpense(input)
        "get_expense_summary" -> expenseSummary()
        else -> "Unknown tool: $name"
    }

    private suspend fun addTrip(input: JSONObject): String {
        val name = input.optString("name").trim()
        val destination = input.optString("destination").trim()
        val startDate = input.optString("startDate").trim()
        val endDate = input.optString("endDate").trim()
        if (name.isEmpty() || destination.isEmpty()) {
            return "add_trip needs at least a name and a destination."
        }
        tripRepository.upsert(
            Trip(name = name, destination = destination, startDate = startDate, endDate = endDate),
        )
        return "Added trip \"$name\" to $destination ($startDate – $endDate)."
    }

    private suspend fun logExpense(input: JSONObject): String {
        val amount = input.optDouble("amount", 0.0)
        if (amount <= 0.0) return "log_expense needs a positive amount."
        val category = runCatching {
            ExpenseCategory.valueOf(input.optString("category").trim().uppercase(Locale.US))
        }.getOrDefault(ExpenseCategory.OTHER)
        val merchant = input.optString("merchant").trim().ifEmpty { "Unknown" }
        val currency = input.optString("currency").trim().ifEmpty { "USD" }
        val date = input.optString("date").trim()
        expenseRepository.add(
            Expense(amount = amount, currency = currency, category = category, merchant = merchant, date = date),
        )
        return "Logged $currency ${money(amount)} at $merchant (${category.label})."
    }

    private suspend fun expenseSummary(): String {
        val expenses = expenseRepository.observeExpenses().first()
        if (expenses.isEmpty()) return "No expenses logged yet."
        val total = expenses.sumOf { it.amount }
        val top = expenses.groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
            .maxByOrNull { it.value }
        val currency = expenses.firstOrNull()?.currency ?: "USD"
        return buildString {
            append("Total $currency ${money(total)} across ${expenses.size} item(s).")
            if (top != null) append(" Largest category: ${top.key.label} ($currency ${money(top.value)}).")
        }
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** Anthropic tool-definition schema sent on every IRIS request. */
    val schema: JSONArray = JSONArray()
        .put(
            tool(
                name = "add_trip",
                description = "Add a new trip to the user's itinerary. Use when the user asks to create or plan a trip.",
                properties = JSONObject()
                    .put("name", strProp("Short trip name, e.g. \"Atlanta Board Meeting\""))
                    .put("destination", strProp("Destination city or place"))
                    .put("startDate", strProp("Start date, ISO-8601, e.g. 2026-07-14"))
                    .put("endDate", strProp("End date, ISO-8601, e.g. 2026-07-17")),
                required = listOf("name", "destination", "startDate", "endDate"),
            ),
        )
        .put(
            tool(
                name = "log_expense",
                description = "Record a travel expense for the user. Use when the user reports a purchase or cost.",
                properties = JSONObject()
                    .put("amount", numProp("Amount in the given currency"))
                    .put("currency", strProp("ISO currency code, default USD"))
                    .put(
                        "category",
                        strProp("One of: FOOD, TRANSPORT, ACCOMMODATION, ENTERTAINMENT, BUSINESS, SHOPPING, MEDICAL, MILEAGE, OTHER"),
                    )
                    .put("merchant", strProp("Where the money was spent"))
                    .put("date", strProp("Date, ISO-8601, e.g. 2026-07-14")),
                required = listOf("amount", "category", "merchant", "date"),
            ),
        )
        .put(
            tool(
                name = "get_expense_summary",
                description = "Summarize the user's logged expenses (total and largest category). Use when asked about spending.",
                properties = JSONObject(),
                required = emptyList(),
            ),
        )

    private fun tool(name: String, description: String, properties: JSONObject, required: List<String>): JSONObject =
        JSONObject()
            .put("name", name)
            .put("description", description)
            .put(
                "input_schema",
                JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", JSONArray(required)),
            )

    private fun strProp(description: String) = JSONObject().put("type", "string").put("description", description)
    private fun numProp(description: String) = JSONObject().put("type", "number").put("description", description)
}
