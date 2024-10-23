package app.versta.translate.ui.component

import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldLargeHeader(
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
                scrollBehavior = scrollBehavior,
            )
        },
        content = { innerPadding ->
            Box (
                modifier = Modifier.padding(innerPadding)
            ) {
                content(innerPadding, scrollBehavior.nestedScrollConnection)
            }
        }
    )
}

@Composable
@Preview
fun ScaffoldLargeHeaderPreview() {
    ScaffoldLargeHeader(
        title = {
            Text(
                text = "Welcome back",
            )
        },
    )
}