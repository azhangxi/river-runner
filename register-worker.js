if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("./service-worker.js", { updateViaCache: "none" }).catch((error) => {
    console.error("River Runner offline setup failed", error);
  });
}
