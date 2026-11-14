# Website

Static site for [https://hellenicdev.eu/pebbles/](https://hellenicdev.eu/pebbles/). Plain HTML, CSS, and JavaScript. No build step.

Open `index.html` in a browser, or serve this folder:

```sh
python3 -m http.server 8080
```

Asset paths are relative, so the same files work at the `/pebbles/` project-site path and when opened locally. Do not add a `CNAME` file here: `hellenicdev.eu` is the user-site domain, and this project is served under `/pebbles/`.

The waitlist form posts to `https://api.hellenicdev.eu/waitlist`. The build log reads public GitHub releases for `hellenicdev/pebbles` in the browser.
