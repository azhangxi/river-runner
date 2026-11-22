import { createHash } from "node:crypto";
import { copyFile, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const dist = fileURLToPath(new URL("./build/dist/js/productionExecutable/", import.meta.url));
const root = fileURLToPath(new URL("../", import.meta.url));
const audio = join(dist, "audio");
const sourceAudio = join(root, "app/src/main/res/raw");

await copyFile(join(root, "CHANGELOG.md"), join(dist, "CHANGELOG.md"));
await copyFile(join(root, "LICENSE"), join(dist, "LICENSE"));
await mkdir(audio, { recursive: true });
for (const file of await readdir(sourceAudio)) {
  if (file.endsWith(".wav")) await copyFile(join(sourceAudio, file), join(audio, file));
}

const files = await listFiles(dist);
const required = [
  "index.html", "river-runner.js", "app.css", "reference.css",
  "manifest.webmanifest", "service-worker.js", "register-worker.js",
];
for (const file of required) {
  if (!files.includes(file)) throw new Error(`Missing build output: ${file}`);
}

const hash = createHash("sha256");
for (const file of files) {
  hash.update(file).update("\0").update(await readFile(join(dist, file))).update("\0");
}
const manifest = { version: hash.digest("hex"), files };
await writeFile(join(dist, "precache.js"), `self.riverRunnerPrecache = ${JSON.stringify(manifest)};\n`);

async function listFiles(directory, prefix = "") {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const relative = prefix + entry.name;
    if (entry.isDirectory()) {
      files.push(...await listFiles(join(directory, entry.name), relative + "/"));
    } else if (entry.isFile() && relative !== "precache.js" && !relative.endsWith(".map")) {
      files.push(relative);
    }
  }
  return files.sort();
}
