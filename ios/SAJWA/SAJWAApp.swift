import SwiftUI
import WebKit
import CoreLocation

@main
struct SAJWAApp: App {
    var body: some Scene { WindowGroup { ContentView() } }
}

struct ContentView: View {
    var body: some View {
        SAJWAWebView()
            .ignoresSafeArea()
            .preferredColorScheme(.light)
}

struct SAJWAWebView: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIView(context: Context) -> WKWebView {
        let controller = WKUserContentController()
        controller.add(context.coordinator, name: "sajwa")
        let bridge = """
        window.SajwaNative = new Proxy({}, { get: (_, method) => (...args) => window.webkit.messageHandlers.sajwa.postMessage({method:String(method),args:args}) });
        """
        controller.addUserScript(WKUserScript(source: bridge, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        let config = WKWebViewConfiguration()
        config.userContentController = controller
        let web = WKWebView(frame: .zero, configuration: config)
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 0.96, green: 0.95, blue: 0.92, alpha: 1)
        if let url = Bundle.main.url(forResource: "index", withExtension: "html") {
            web.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
        context.coordinator.webView = web
        return web
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    final class Coordinator: NSObject, WKScriptMessageHandler, CLLocationManagerDelegate {
        weak var webView: WKWebView?
        private let location = CLLocationManager()
        private var compassActive = false
        override init() { super.init(); location.delegate = self }
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: Any], let method = body["method"] as? String else { return }
            switch method {
            case "requestLocation":
                location.requestWhenInUseAuthorization()
                location.requestLocation()
            case "startCompass":
                compassActive = true
                location.requestWhenInUseAuthorization()
                if CLLocationManager.headingAvailable() { location.startUpdatingHeading() }
                if location.authorizationStatus == .notDetermined { location.requestWhenInUseAuthorization() }
            case "stopCompass":
                compassActive = false
                location.stopUpdatingHeading()
            case "getDiagnostics":
                evaluate("window.onSajwaDiagnostics && window.onSajwaDiagnostics({platform:'ios',location:'CoreLocation',compass:CLLocationManager.headingAvailable()})")
            default:
                // Android-only alarm/audio/diagnostic operations intentionally remain no-ops on this first iOS preview.
                break
            }
        }
        func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
            guard let point = locations.last else { return }
            let lat = point.coordinate.latitude, lon = point.coordinate.longitude
            evaluate("window.onSajwaIOSLocation && window.onSajwaIOSLocation(\(lat),\(lon))")
        }
        func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
            evaluate("window.onSajwaIOSLocationError && window.onSajwaIOSLocationError('Location unavailable')")
        }
        func locationManager(_ manager: CLLocationManager, didUpdateHeading heading: CLHeading) {
            guard compassActive else { return }
            let degrees = heading.trueHeading >= 0 ? heading.trueHeading : heading.magneticHeading
            evaluate("window.onSajwaCompass && window.onSajwaCompass(0,\(degrees))")
        }
        private func evaluate(_ js: String) { DispatchQueue.main.async { self.webView?.evaluateJavaScript(js) } }
    }
}
