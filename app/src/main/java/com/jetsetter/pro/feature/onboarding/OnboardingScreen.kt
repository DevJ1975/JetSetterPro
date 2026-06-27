package com.jetsetter.pro.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Flight,
        title = "Welcome to JetSetter Pro",
        body = "Your executive travel companion — flights, itinerary, expenses, and more, all in one place.",
    ),
    OnboardingPage(
        icon = Icons.Filled.AutoAwesome,
        title = "Meet IRIS",
        body = "Your AI travel concierge. Ask about flights, packing, visas, or expenses anytime — she's already read your itinerary.",
    ),
    OnboardingPage(
        icon = Icons.Filled.Luggage,
        title = "Travel, handled",
        body = "Track every trip, log expenses on the go, and stay ahead of disruptions. Let's get you set up.",
    ),
)

/** First-run onboarding. Completing/skipping flips the DataStore flag via [OnboardingViewModel]. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    OnboardingContent(onFinish = viewModel::complete)
}

@Composable
private fun OnboardingContent(onFinish: () -> Unit) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = spacing.large),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onFinish) {
                Text("Skip", style = JetTheme.typography.label, color = colors.textSecondary)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            OnboardingPageView(onboardingPages[page])
        }

        PageIndicator(
            pageCount = onboardingPages.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier.padding(bottom = spacing.large),
        )

        Button(
            onClick = {
                if (isLastPage) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = if (isLastPage) "Get started" else "Next",
                style = JetTheme.typography.cardTitle,
            )
        }
        Spacer(Modifier.height(spacing.large))
    }
}

@Composable
private fun OnboardingPageView(page: OnboardingPage) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(Modifier.height(spacing.xlarge))
        Text(
            text = page.title,
            style = JetTheme.typography.displayTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.medium))
        Text(
            text = page.body,
            style = JetTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (selected) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.accent else colors.surfaceElevated),
            )
        }
    }
}

@Preview(name = "Onboarding – Dark", showBackground = true)
@Composable
private fun OnboardingPreview() {
    JetSetterTheme(darkTheme = true) {
        OnboardingContent(onFinish = {})
    }
}
