package com.jetsetter.pro.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.jetsetter.pro.feature.airportmap.AirportMapScreen
import com.jetsetter.pro.feature.assistant.AssistantScreen
import com.jetsetter.pro.feature.booking.BookingScreen
import com.jetsetter.pro.feature.carbontracker.CarbonTrackerScreen
import com.jetsetter.pro.feature.checkin.CheckInScreen
import com.jetsetter.pro.feature.currencytracker.CurrencyTrackerScreen
import com.jetsetter.pro.feature.departureoptimizer.DepartureOptimizerScreen
import com.jetsetter.pro.feature.disruption.DisruptionScreen
import com.jetsetter.pro.feature.documentvault.DocumentVaultScreen
import com.jetsetter.pro.feature.expenseexport.ExpenseExportScreen
import com.jetsetter.pro.feature.flightboard.FlightBoardScreen
import com.jetsetter.pro.feature.flighttracker.FlightTrackerScreen
import com.jetsetter.pro.feature.groundtransport.GroundTransportScreen
import com.jetsetter.pro.feature.identityvault.IdentityVaultScreen
import com.jetsetter.pro.feature.inflight.InFlightScreen
import com.jetsetter.pro.feature.intelligence.IntelligenceScreen
import com.jetsetter.pro.feature.localexperience.LocalExperienceScreen
import com.jetsetter.pro.feature.loyaltyvault.LoyaltyVaultScreen
import com.jetsetter.pro.feature.luggagetracker.LuggageTrackerScreen
import com.jetsetter.pro.feature.offlinekit.OfflineKitScreen
import com.jetsetter.pro.feature.rentalcar.RentalCarScreen
import com.jetsetter.pro.feature.subscription.SubscriptionScreen
import com.jetsetter.pro.feature.translator.TranslatorScreen
import com.jetsetter.pro.feature.travelessentials.TravelEssentialsScreen
import com.jetsetter.pro.feature.traveljournal.TravelJournalScreen
import com.jetsetter.pro.feature.travelwallet.TravelWalletScreen
import com.jetsetter.pro.feature.visalookup.VisaLookupScreen

/**
 * One entry per ported feature module. This single list is the source of truth for both
 * navigation ([JetNavHost] registers a `composable` per entry) and the **More → Features**
 * menu ([com.jetsetter.pro.feature.more.MoreScreen] renders a row per entry). Add a module here
 * and it is automatically reachable + navigable.
 */
data class FeatureEntry(
    val route: String,
    val title: String,
    val group: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit,
)

val featureCatalog: List<FeatureEntry> = listOf(
    // Travel day
    FeatureEntry("flighttracker", "Flight Tracker", "Travel day", Icons.Filled.FlightTakeoff) { FlightTrackerScreen() },
    FeatureEntry("flightboard", "Flight Board", "Travel day", Icons.Filled.Schedule) { FlightBoardScreen() },
    FeatureEntry("checkin", "Check-In", "Travel day", Icons.Filled.HowToReg) { CheckInScreen() },
    FeatureEntry("departureoptimizer", "Departure Optimizer", "Travel day", Icons.Filled.Timer) { DepartureOptimizerScreen() },
    FeatureEntry("disruption", "Disruption Monitor", "Travel day", Icons.Filled.Warning) { DisruptionScreen() },
    FeatureEntry("inflight", "In-Flight", "Travel day", Icons.Filled.Flight) { InFlightScreen() },
    FeatureEntry("groundtransport", "Ground Transport", "Travel day", Icons.Filled.DirectionsCar) { GroundTransportScreen() },
    // Plan
    FeatureEntry("booking", "Hotel Booking", "Plan", Icons.Filled.Hotel) { BookingScreen() },
    FeatureEntry("rentalcar", "Rental Cars", "Plan", Icons.Filled.DirectionsCar) { RentalCarScreen() },
    FeatureEntry("visalookup", "Visa & Entry", "Plan", Icons.Filled.Public) { VisaLookupScreen() },
    FeatureEntry("travelessentials", "Travel Essentials", "Plan", Icons.Filled.Info) { TravelEssentialsScreen() },
    FeatureEntry("localexperience", "Local Experiences", "Plan", Icons.Filled.Place) { LocalExperienceScreen() },
    FeatureEntry("translator", "Translator", "Plan", Icons.Filled.Translate) { TranslatorScreen() },
    FeatureEntry("airportmap", "Airport Guide", "Plan", Icons.Filled.Map) { AirportMapScreen() },
    FeatureEntry("currencytracker", "Currency", "Plan", Icons.Filled.CurrencyExchange) { CurrencyTrackerScreen() },
    FeatureEntry("carbontracker", "Carbon Footprint", "Plan", Icons.Filled.Eco) { CarbonTrackerScreen() },
    // Wallet & documents
    FeatureEntry("travelwallet", "Travel Wallet", "Wallet & documents", Icons.Filled.Wallet) { TravelWalletScreen() },
    FeatureEntry("loyaltyvault", "Loyalty Vault", "Wallet & documents", Icons.Filled.CardMembership) { LoyaltyVaultScreen() },
    FeatureEntry("documentvault", "Document Vault", "Wallet & documents", Icons.Filled.Lock) { DocumentVaultScreen() },
    FeatureEntry("identityvault", "Identity Vault", "Wallet & documents", Icons.Filled.Badge) { IdentityVaultScreen() },
    // Trip
    FeatureEntry("traveljournal", "Trip Journal", "Trip", Icons.Filled.Book) { TravelJournalScreen() },
    FeatureEntry("luggagetracker", "Luggage Tracker", "Trip", Icons.Filled.Luggage) { LuggageTrackerScreen() },
    FeatureEntry("assistant", "Assistant", "Trip", Icons.Filled.SmartToy) { AssistantScreen() },
    FeatureEntry("intelligence", "Travel Intelligence", "Trip", Icons.Filled.Insights) { IntelligenceScreen() },
    FeatureEntry("offlinekit", "Offline Kit", "Trip", Icons.Filled.CloudDownload) { OfflineKitScreen() },
    // Account
    FeatureEntry("expenseexport", "Expense Export", "Account", Icons.Filled.Upload) { ExpenseExportScreen() },
    FeatureEntry("subscription", "JetSetter Pro", "Account", Icons.Filled.Star) { SubscriptionScreen() },
)
