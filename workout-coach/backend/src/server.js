import { createServer } from 'node:http';
import { createHandler, loadConfig } from './app.js';

const config = loadConfig();
if (config.appToken.length < 24) {
  console.error('APP_TOKEN must be set to a random string of at least 24 characters (e.g. `openssl rand -hex 32`).');
  process.exit(1);
}
if (Object.keys(config.providers).length === 0) {
  console.error('No AI provider configured. Set e.g. GEMINI_API_KEY and GEMINI_MODEL (see .env.example).');
  process.exit(1);
}

createServer(createHandler(config)).listen(config.port, () => {
  // Never log keys or the app token.
  console.log(JSON.stringify({ msg: 'workout-coach backend listening', port: config.port, providers: Object.keys(config.providers), default: config.defaultProvider }));
});
