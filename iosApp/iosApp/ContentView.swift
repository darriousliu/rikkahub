import RikkaHubShared
import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("RikkaHub")
                .font(.title)
            Text("Compose Multiplatform iOS shell")
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
