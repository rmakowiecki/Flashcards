// The local preview (`wasmJsBrowserDevelopmentRun`): port 8080, Kotlin's default, is the Firestore
// emulator's (firebase.json), so the site is served on 8090, and no browser window opens on its own.
// Every path serves index.html, as Firebase Hosting's rewrite does, so /privacy loads on a reload.
// It listens on the local network too, so a phone can open it and show a mobile browser's real address bar.
if (config.devServer) {
    config.devServer.host = "0.0.0.0";
    config.devServer.allowedHosts = "all";
    config.devServer.port = 8090;
    config.devServer.open = false;
    config.devServer.historyApiFallback = true;
}
