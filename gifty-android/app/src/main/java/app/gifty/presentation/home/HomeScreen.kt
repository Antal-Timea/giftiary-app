package app.gifty.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.gifty.R
import app.gifty.presentation.theme.GiftyTheme

@Composable
fun HomeScreen(onOpenPeople: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.headlineSmall,
            )
            Button(onClick = onOpenPeople) {
                Text(stringResource(R.string.home_open_people))
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    GiftyTheme {
        HomeScreen(onOpenPeople = {})
    }
}
