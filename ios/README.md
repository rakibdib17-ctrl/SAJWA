# SAJWA iOS preview

This is an initial native iOS WKWebView shell around the existing SAJWA HTML interface.

## Build
GitHub Actions runs on macOS, generates the Xcode project with XcodeGen, and builds an iOS Simulator app. Download the artifact from Actions.

## Important
- The artifact is an iOS Simulator build, **not an installable iPhone IPA**.
- Installing on a physical iPhone requires Apple code signing (Apple Developer account/certificate and provisioning profile), or a suitable personal-device signing workflow from a Mac.
- This first preview provides the existing HTML UI and basic CoreLocation/heading sensor bridge. Android-specific exact alarms, background Adhan service, notification scheduling, and some native diagnostics are not implemented on iOS yet.
- The existing web app expects Android bridge callbacks for some native operations. Prayer-time location callback integration and Qibla calibration need device testing and further iOS adaptation.
