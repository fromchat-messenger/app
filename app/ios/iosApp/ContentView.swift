import UIKit
import Foundation
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    let startAtProfileUserId: Int?
    let startAtProfileUsername: String?

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            startAtProfileUserId: startAtProfileUserId.map { KotlinInt(value: Int32($0)) },
            startAtProfileUsername: startAtProfileUsername
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @State private var startAtProfileUserId: Int?
    @State private var startAtProfileUsername: String?
    @State private var profileNonce = UUID()
    @State private var showProfileLinkError = false
    @State private var profileLinkErrorMessage: String? = nil

    var body: some View {
        ComposeView(
            startAtProfileUserId: startAtProfileUserId,
            startAtProfileUsername: startAtProfileUsername
        )
            .id(profileNonce)
            .ignoresSafeArea(.all)
            .onOpenURL { url in
                print("[ProfileLink:iOS] onOpenURL url=\(url.absoluteString)")
                let target = Self.parseProfileDeepLink(url: url)
                print("[ProfileLink:iOS] parse result userId=\(String(describing: target?.userId)) username=\(String(describing: target?.username)) error=\(String(describing: target?.error))")
                guard let parsed = target else { return }
                if let error = parsed.error {
                    profileLinkErrorMessage = error
                    showProfileLinkError = true
                    return
                }
                startAtProfileUserId = parsed.userId
                startAtProfileUsername = parsed.username
                profileNonce = UUID()
            }
            .alert("Profile link", isPresented: $showProfileLinkError) {
                Button("OK", role: .cancel) {
                    showProfileLinkError = false
                }
            } message: {
                Text(profileLinkErrorMessage ?? "Invalid profile link.")
            }
    }

    struct ParsedProfileDeepLink {
        let userId: Int?
        let username: String?
        let error: String?
    }

    static func parseProfileDeepLink(url: URL) -> ParsedProfileDeepLink? {
        print("[ProfileLink:iOS] parseProfileDeepLink start url=\(url)")
        guard url.scheme?.lowercased() == "fromchat",
              url.host == "u" else {
            return nil
        }

        let parts = url.path.split(separator: "/").filter { !$0.isEmpty }
        print("[ProfileLink:iOS] scheme=\(url.scheme ?? "nil") host=\(url.host ?? "nil") path=\(url.path) parts=\(parts.map(String.init))")
        guard parts.count == 1, let rawSegment = parts.first else {
            return ParsedProfileDeepLink(
                userId: nil,
                username: nil,
                error: "Invalid profile link. Use fromchat://u/<id-or-username>."
            )
        }

        let segment = String(rawSegment).removingPercentEncoding?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !segment.isEmpty else {
            return ParsedProfileDeepLink(
                userId: nil,
                username: nil,
                error: "Invalid profile link. Use fromchat://u/<id-or-username>."
            )
        }
        print("[ProfileLink:iOS] parsed segment=\(segment)")

        if let id = Int64(segment), id >= 1 && id <= Int64(Int.max) {
            print("[ProfileLink:iOS] resolved as userId=\(id)")
            return ParsedProfileDeepLink(userId: Int(id), username: nil, error: nil)
        }

        print("[ProfileLink:iOS] resolved as username=\(segment)")
        return ParsedProfileDeepLink(userId: nil, username: segment, error: nil)
    }

}
