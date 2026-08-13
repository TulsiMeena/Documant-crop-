package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.example.ui.components.OnboardingIllustration1
import com.example.ui.components.OnboardingIllustration2
import com.example.ui.components.OnboardingIllustration3
import com.example.ui.components.OnboardingIllustration4
import com.example.ui.components.ScanovaPrimaryButton
import com.example.ui.components.ScanovaTextButton

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }

    val titles = listOf(
        "Scan smarter.",
        "Perfect the corners.",
        "Make every scan clearer.",
        "Ready when you are."
    )

    val subtitles = listOf(
        "Turn ordinary photos and paper documents into clean digital scans.",
        "Automatically detect the document and straighten even angled photos.",
        "Clean, enhance and improve your documents with simple one-tap tools.",
        "Scan, organize and save your documents directly on your phone."
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("onboarding_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage < 3) {
                    ScanovaTextButton(
                        text = "Skip",
                        onClick = onFinishOnboarding,
                        testTag = "onboarding_skip_button"
                    )
                } else {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            // Animated Center Content (Illustration + Text)
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                label = "onboarding_transition",
                modifier = Modifier.weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Illustration
                    when (page) {
                        0 -> OnboardingIllustration1()
                        1 -> OnboardingIllustration2()
                        2 -> OnboardingIllustration3()
                        3 -> OnboardingIllustration4()
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // Title
                    Text(
                        text = titles[page],
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("onboarding_title_$page")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subtitle
                    Text(
                        text = subtitles[page],
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .testTag("onboarding_subtitle_$page")
                    )

                    // Page 4 extra Privacy message
                    if (page == 3) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.testTag("privacy_notice")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Your documents stay on your device.",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Bottom Area: Progress Dots + Next / Get Started Action
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress Dots (● ○ ○ ○)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    for (i in 0..3) {
                        val isSelected = i == currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(if (isSelected) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Action Button
                if (currentPage < 3) {
                    ScanovaPrimaryButton(
                        text = "Next",
                        onClick = { currentPage++ },
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        testTag = "onboarding_next_button"
                    )
                } else {
                    ScanovaPrimaryButton(
                        text = "Get Started",
                        onClick = onFinishOnboarding,
                        testTag = "onboarding_get_started_button"
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
