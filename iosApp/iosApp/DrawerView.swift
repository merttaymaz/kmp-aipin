import SwiftUI

enum Screen: String, CaseIterable {
    case images = "Resimler"
    case audioRecord = "Ses Kaydı"

    var icon: String {
        switch self {
        case .images: return "photo.on.rectangle"
        case .audioRecord: return "mic"
        }
    }
}

private let drawerWidth: CGFloat = 260
private let drawerAnimation: Animation = .easeInOut(duration: 0.25)

struct DrawerView: View {
    @State private var isDrawerOpen = false
    @State private var selectedScreen: Screen = .images

    var body: some View {
        ZStack(alignment: .leading) {
            VStack(spacing: 0) {
                HStack {
                    Button {
                        withAnimation(drawerAnimation) { isDrawerOpen.toggle() }
                    } label: {
                        Image(systemName: "line.3.horizontal")
                            .imageScale(.large)
                            .padding(12)
                    }
                    Text(selectedScreen.rawValue).font(.headline)
                    Spacer()
                }

                switch selectedScreen {
                case .images: ListView()
                case .audioRecord: AudioRecordView()
                }
            }

            if isDrawerOpen {
                Color.black.opacity(0.3)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(drawerAnimation) { isDrawerOpen = false }
                    }
            }

            VStack(alignment: .leading, spacing: 0) {
                Text("Menu")
                    .font(.title2.bold())
                    .padding(.horizontal, 20)
                    .padding(.top, 60)
                    .padding(.bottom, 20)

                ForEach(Screen.allCases, id: \.self) { screen in
                    let isSelected = selectedScreen == screen
                    Button {
                        selectedScreen = screen
                        withAnimation(drawerAnimation) { isDrawerOpen = false }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: screen.icon).frame(width: 24)
                            Text(screen.rawValue)
                        }
                        .foregroundColor(isSelected ? .blue : .primary)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(isSelected ? Color.blue.opacity(0.1) : .clear)
                    }
                }
                Spacer()
            }
            .frame(width: drawerWidth)
            .background(Color(UIColor.systemBackground))
            .offset(x: isDrawerOpen ? 0 : -drawerWidth)
            .animation(drawerAnimation, value: isDrawerOpen)
        }
    }
}
