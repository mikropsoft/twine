/*
 * Copyright 2023 Sasikanth Miriyampalli
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
package dev.sasikanth.rss.reader.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.SnackbarHost
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.moriatsushi.insetsx.ExperimentalSoftwareKeyboardApi
import com.moriatsushi.insetsx.ime
import com.moriatsushi.insetsx.navigationBars
import com.moriatsushi.insetsx.statusBarsPadding
import dev.icerock.moko.resources.compose.stringResource
import dev.sasikanth.rss.reader.CommonRes
import dev.sasikanth.rss.reader.components.bottomsheet.BottomSheetScaffold
import dev.sasikanth.rss.reader.components.bottomsheet.BottomSheetScaffoldState
import dev.sasikanth.rss.reader.components.bottomsheet.BottomSheetState
import dev.sasikanth.rss.reader.components.bottomsheet.BottomSheetValue
import dev.sasikanth.rss.reader.components.bottomsheet.rememberBottomSheetScaffoldState
import dev.sasikanth.rss.reader.components.bottomsheet.rememberBottomSheetState
import dev.sasikanth.rss.reader.database.Feed
import dev.sasikanth.rss.reader.database.PostWithMetadata
import dev.sasikanth.rss.reader.feeds.ui.BottomSheetPrimaryActionButton
import dev.sasikanth.rss.reader.feeds.ui.FeedsBottomSheet
import dev.sasikanth.rss.reader.home.HomeEffect
import dev.sasikanth.rss.reader.home.HomeErrorType
import dev.sasikanth.rss.reader.home.HomeEvent
import dev.sasikanth.rss.reader.home.HomePresenter
import dev.sasikanth.rss.reader.home.HomePresenterFactory
import dev.sasikanth.rss.reader.home.HomeState
import dev.sasikanth.rss.reader.ui.AppTheme
import dev.sasikanth.rss.reader.utils.LocalStringReader
import dev.sasikanth.rss.reader.utils.StringReader
import dev.sasikanth.rss.reader.utils.inverseProgress
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

private val BOTTOM_SHEET_PEEK_HEIGHT = 112.dp
private val BOTTOM_SHEET_CORNER_SIZE = 32.dp

@Composable
fun HomeScreen(
  homePresenterFactory: HomePresenterFactory,
  onFeaturedItemChange: (imageUrl: String?) -> Unit,
  openLink: (String) -> Unit
) {
  val coroutineScope = rememberCoroutineScope()

  val homePresenter = homePresenterFactory.presenter
  val state by homePresenter.state.collectAsState()
  val (featuredPosts, posts) = state

  val bottomSheetState =
    rememberBottomSheetState(
      state.feedsSheetState,
      confirmStateChange = {
        homePresenter.dispatch(HomeEvent.FeedsSheetStateChanged(it))
        true
      }
    )
  val bottomSheetScaffoldState =
    rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

  val bottomSheetSwipeTransition =
    updateTransition(
      targetState = bottomSheetState.offsetProgress,
      label = "Bottom Sheet Swipe Progress"
    )
  val bottomSheetCornerSize by
    bottomSheetSwipeTransition.animateDp { BOTTOM_SHEET_CORNER_SIZE * it.inverseProgress() }

  val stringReader = LocalStringReader.current

  LaunchedEffect(Unit) {
    homePresenter.effects.collect { effect ->
      when (effect) {
        is HomeEffect.OpenPost -> {
          openLink(effect.post.link)
        }
        HomeEffect.MinimizeSheet -> {
          bottomSheetState.collapse()
        }
        is HomeEffect.ShowError -> {
          displayErrorMessage(effect, stringReader, bottomSheetScaffoldState)
        }
      }
    }
  }

  LaunchedEffect(bottomSheetState.targetValue) {
    if (bottomSheetState.targetValue == BottomSheetValue.Collapsed) {
      homePresenter.dispatch(HomeEvent.OnCancelAddFeedClicked)
    }
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
  ) {
    BottomSheetScaffold(
      scaffoldState = bottomSheetScaffoldState,
      content = {
        HomeScreenContent(
          featuredPosts = featuredPosts,
          posts = posts,
          selectedFeed = state.selectedFeed,
          isRefreshing = state.isRefreshing,
          onSwipeToRefresh = { homePresenter.dispatch(HomeEvent.OnSwipeToRefresh) },
          onPostClicked = { homePresenter.dispatch(HomeEvent.OnPostClicked(it)) },
          onFeaturedItemChange = onFeaturedItemChange
        ) {
          coroutineScope.launch { bottomSheetState.expand() }
        }
      },
      sheetContent = {
        FeedsBottomSheet(
          feedsPresenter = homePresenterFactory.feedsPresenter,
          bottomSheetSwipeTransition = bottomSheetSwipeTransition,
          showingFeedLinkEntry = state.canShowFeedLinkEntry,
          closeSheet = { coroutineScope.launch { bottomSheetState.collapse() } }
        )
      },
      snackbarHost = {
        SnackbarHost(
          hostState = it,
          modifier =
            Modifier.padding(bottom = 112.dp)
              .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        )
      },
      backgroundColor = Color.Transparent,
      sheetBackgroundColor = AppTheme.colorScheme.tintedBackground,
      sheetContentColor = AppTheme.colorScheme.tintedForeground,
      sheetElevation = 0.dp,
      sheetPeekHeight = bottomSheetPeekHeight(),
      sheetShape =
        RoundedCornerShape(topStart = bottomSheetCornerSize, topEnd = bottomSheetCornerSize),
    )

    PrimaryActionButtonContainer(bottomSheetSwipeTransition, state, homePresenter, bottomSheetState)
  }
}

@Composable
private fun HomeScreenContent(
  featuredPosts: ImmutableList<PostWithMetadata>,
  posts: ImmutableList<PostWithMetadata>,
  selectedFeed: Feed?,
  isRefreshing: Boolean,
  onSwipeToRefresh: () -> Unit,
  onPostClicked: (PostWithMetadata) -> Unit,
  onFeaturedItemChange: (imageUrl: String?) -> Unit,
  onNoFeedsSwipeUp: () -> Unit,
) {
  val hasContent = featuredPosts.isNotEmpty() || posts.isNotEmpty()
  if (hasContent) {
    val swipeRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = onSwipeToRefresh)

    Box(Modifier.fillMaxSize().pullRefresh(swipeRefreshState)) {
      val listState = rememberLazyListState()
      val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

      PostsList(
        featuredPosts = featuredPosts,
        posts = posts,
        selectedFeed = selectedFeed,
        onPostClicked = onPostClicked,
        onFeaturedItemChange = onFeaturedItemChange,
        listState = listState
      )

      PullRefreshIndicator(
        refreshing = isRefreshing,
        state = swipeRefreshState,
        modifier = Modifier.statusBarsPadding().align(Alignment.TopCenter)
      )

      ScrollToTopButton(visible = showScrollToTop) { listState.animateScrollToItem(0) }
    }
  } else {
    NoFeeds(onNoFeedsSwipeUp)
  }
}

/**
 * Since we want the all button to not move when expanding and collapsing bottom bar and transform
 * to add button. We are not placing it inside the bottom sheet content and instead place it above
 * the home screen and bottom bar content essentially.
 *
 * We might have to replace this once bottom sheet exposes height or offset from bottom which would
 * allow us to modify the offset of this item in the sheet itself instead of using workarounds.
 *
 * track: https://issuetracker.google.com/issues/209825720
 */
@OptIn(ExperimentalSoftwareKeyboardApi::class)
@Composable
private fun BoxScope.PrimaryActionButtonContainer(
  bottomSheetSwipeTransition: Transition<Float>,
  state: HomeState,
  viewModel: HomePresenter,
  bottomSheetState: BottomSheetState
) {
  // (1/0.2) 0.2 is our threshold in the 0..1 range
  val bottomSheetContentTransitionThreshold = 5
  val primaryActionStartPadding =
    (24.dp -
        (4 *
            (bottomSheetSwipeTransition.currentState * bottomSheetContentTransitionThreshold)
              .inverseProgress())
          .dp)
      .coerceIn(20.dp, 24.dp)

  val windowInsetsPaddingModifier =
    Modifier.windowInsetsPadding(
      WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .union(WindowInsets.ime.only(WindowInsetsSides.Bottom))
    )

  Box(Modifier.padding(start = primaryActionStartPadding).align(Alignment.BottomStart)) {
    if (state.canShowFeedLinkEntry) {
      FeedLinkInputField(
        modifier = windowInsetsPaddingModifier.padding(bottom = 24.dp, end = 24.dp),
        isFetchingFeed = state.isFetchingFeed,
        onAddFeed = { viewModel.dispatch(HomeEvent.AddFeed(it)) },
        onCancelFeedEntryClicked = { viewModel.dispatch(HomeEvent.OnCancelAddFeedClicked) }
      )
    } else {
      BottomSheetPrimaryActionButton(
        modifier =
          windowInsetsPaddingModifier.graphicsLayer {
            translationY = (4 * bottomSheetSwipeTransition.currentState).dp.toPx()
          },
        selected = state.isAllFeedsSelected,
        bottomSheetSwipeProgress =
          (bottomSheetSwipeTransition.currentState * bottomSheetContentTransitionThreshold)
            .inverseProgress(),
        bottomSheetCurrentState = bottomSheetState.currentValue,
        bottomSheetTargetState = bottomSheetState.targetValue
      ) {
        viewModel.dispatch(HomeEvent.OnPrimaryActionClicked)
      }
    }
  }
}

@Composable
private fun BoxScope.ScrollToTopButton(visible: Boolean, onClick: suspend () -> Unit) {
  val coroutineScope = rememberCoroutineScope()
  AnimatedVisibility(
    visible = visible,
    enter = slideInVertically { it / 2 },
    exit = slideOutVertically { it / 2 },
    modifier = Modifier.Companion.align(Alignment.BottomEnd)
  ) {
    ExtendedFloatingActionButton(
      modifier =
        Modifier.windowInsetsPadding(WindowInsets.navigationBars)
          .padding(end = 24.dp, bottom = BOTTOM_SHEET_PEEK_HEIGHT + 24.dp),
      shape = RoundedCornerShape(50),
      containerColor = AppTheme.colorScheme.tintedBackground,
      contentColor = AppTheme.colorScheme.tintedForeground,
      text = {
        Text(
          stringResource(CommonRes.strings.scroll_to_top),
          color = AppTheme.colorScheme.tintedForeground
        )
      },
      icon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
      onClick = { coroutineScope.launch { onClick() } }
    )
  }
}

@Composable
private fun NoFeeds(onNoFeedsSwipeUp: () -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxSize().padding(bottom = 136.dp).pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          if (dragAmount.y < 0) {
            onNoFeedsSwipeUp()
          }
        }
      },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = stringResource(CommonRes.strings.no_feeds),
      style = MaterialTheme.typography.headlineMedium,
      color = AppTheme.colorScheme.textEmphasisHigh
    )

    Spacer(Modifier.requiredHeight(8.dp))

    Text(
      text = stringResource(CommonRes.strings.swipe_up_get_started),
      style = MaterialTheme.typography.labelLarge,
      color = AppTheme.colorScheme.textEmphasisMed
    )

    Spacer(Modifier.requiredHeight(12.dp))

    Icon(
      imageVector = Icons.Filled.KeyboardArrowUp,
      contentDescription = null,
      tint = AppTheme.colorScheme.tintedForeground
    )
  }
}

@Composable
private fun bottomSheetPeekHeight() =
  BOTTOM_SHEET_PEEK_HEIGHT + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

private suspend fun displayErrorMessage(
  effect: HomeEffect.ShowError,
  stringReader: StringReader,
  bottomSheetScaffoldState: BottomSheetScaffoldState
) {
  val message =
    when (val errorType = effect.homeErrorType) {
      HomeErrorType.UnknownFeedType -> stringReader.string(CommonRes.strings.error_unsupported_feed)
      HomeErrorType.FailedToParseXML -> stringReader.string(CommonRes.strings.error_malformed_xml)
      HomeErrorType.Timeout -> stringReader.string(CommonRes.strings.error_request_timeout)
      is HomeErrorType.Unknown -> errorType.e.message
    }

  if (message != null) {
    bottomSheetScaffoldState.snackbarHostState.showSnackbar(message = message)
  }
}
