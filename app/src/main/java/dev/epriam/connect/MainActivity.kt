package dev.epriam.connect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.epriam.connect.theme.EPriamConnectTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      EPriamConnectTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          ProtocolFoundationScreen()
        }
      }
    }
  }
}

@Composable
private fun ProtocolFoundationScreen() {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text("ePriam Connect", style = MaterialTheme.typography.headlineLarge)
    Text(
      "Protocol foundation ready. Bluetooth controls are being connected next.",
      style = MaterialTheme.typography.bodyLarge,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ProtocolFoundationPreview() {
  EPriamConnectTheme { ProtocolFoundationScreen() }
}
