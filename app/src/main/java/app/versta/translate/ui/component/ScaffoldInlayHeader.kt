package app.versta.translate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldInlayHeader(
    title: @Composable () -> Unit = {},
    actions: @Composable (RowScope.() -> Unit) = {},
    navigationIcon: @Composable (() -> Unit) = {},
    content: @Composable (innerPadding: PaddingValues, scrollConnection: NestedScrollConnection) -> Unit = { _, _ -> }
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = title,
                actions = actions,
                navigationIcon = navigationIcon,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .background(color = MaterialTheme.colorScheme.primary)
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        .padding(
                            top = MaterialTheme.spacing.large,
                            bottom = innerPadding.calculateBottomPadding()
                        )
                        .fillMaxSize()
                ) {
                    content(innerPadding, scrollBehavior.nestedScrollConnection)
                }
            }
        }
    )
}

@Composable
@Preview
fun ScaffoldInlayHeaderPreview() {
    ScaffoldInlayHeader(
        title = {
            Text(
                text = "Welcome back",
            )
        },
    )
}