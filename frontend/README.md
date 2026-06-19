# frontend

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd) 
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Type Support for `.vue` Imports in TS

TypeScript cannot handle type information for `.vue` imports by default, so we replace the `tsc` CLI with `vue-tsc` for type checking. In editors, we need [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) to make the TypeScript language service aware of `.vue` types.

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

## Mobile App Testing

There are two different "mobile" test paths:

### 1. Browser mobile viewport

Use this for layout checks only. Native features such as biometric login and push
notifications will not run in a normal browser.

```sh
npm run dev -- --host 127.0.0.1 --port 5173
```

Open `http://127.0.0.1:5173/login` and switch the browser to a mobile viewport.
The API client derives the backend host from the frontend host, so this page
calls `http://127.0.0.1:8080/api`.

### 2. Android native shell

Use this for Capacitor behavior such as student-only routing, biometric login,
and push permission flows.

Prerequisites:

- Backend running on the Mac: `./gradlew bootRun` from the repository root.
- Android emulator or a USB device running and visible to adb.

Useful commands:

```sh
npm run android:devices
npm run android:reverse
npm run android:build
npm run android:install
```

`android:reverse` maps the emulator/device's `localhost:8080` to the Mac's
backend. If login fails in the native app, first confirm `npm run
android:devices` shows a device and then rerun `npm run android:reverse`.

Sample local logins:

- Student: ID `1`, PIN `1111`
- Teacher: username `suhui`, PIN `123456`

### Type-Check, Compile and Minify for Production

```sh
npm run build
```

### Lint with [ESLint](https://eslint.org/)

```sh
npm run lint
```
