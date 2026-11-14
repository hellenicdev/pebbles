(function () {
  var root = document.documentElement;
  var storageKey = "pebbles-theme";

  function systemTheme() {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }

  function activeTheme() {
    var set = root.getAttribute("data-theme");
    if (set === "light" || set === "dark") return set;
    return systemTheme();
  }

  function labelFor(theme) {
    return theme === "dark" ? "Use light colours" : "Use dark colours";
  }

  var toggle = document.querySelector("#theme-toggle");
  if (toggle) {
    toggle.hidden = false;
    toggle.textContent = labelFor(activeTheme());
    toggle.addEventListener("click", function () {
      var next = activeTheme() === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      try {
        localStorage.setItem(storageKey, next);
      } catch (err) {
        /* private mode */
      }
      toggle.textContent = labelFor(next);
    });
  }

  var form = document.querySelector("#waitlist-form");
  if (form) {
    var status = document.querySelector("#form-status");
    var submit = form.querySelector("button[type='submit']");
    form.addEventListener("submit", function (event) {
      event.preventDefault();
      var emailInput = form.querySelector("#email");
      var confirmInput = form.querySelector("#confirm");
      if (!emailInput.checkValidity()) {
        emailInput.reportValidity();
        return;
      }
      submit.disabled = true;
      status.className = "form-status";
      status.textContent = "Sending…";
      fetch("https://api.hellenicdev.eu/waitlist", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          email: emailInput.value.trim(),
          confirm: confirmInput ? confirmInput.value : ""
        })
      })
        .then(function (response) {
          return response.json().catch(function () {
            return {};
          }).then(function (data) {
            return { response: response, data: data };
          });
        })
        .then(function (result) {
          if (result.response.ok && result.data.ok) {
            status.className = "form-status is-ok";
            status.textContent = "You're on the list. We'll write when there's news.";
            form.reset();
            return;
          }
          status.className = "form-status is-err";
          status.textContent =
            typeof result.data.error === "string" && result.data.error.length < 180
              ? result.data.error
              : "We couldn't add you just now. Please try again.";
        })
        .catch(function () {
          status.className = "form-status is-err";
          status.textContent = "We couldn't reach the waitlist. Check your connection and try again.";
        })
        .then(function () {
          submit.disabled = false;
        });
    });
  }

  var releaseList = document.querySelector("#release-list");
  var releaseStatus = document.querySelector("#release-status");
  if (releaseList && releaseStatus) loadReleases(releaseList, releaseStatus);

  function loadReleases(list, status) {
    status.textContent = "Fetching the latest releases…";
    fetch("https://api.github.com/repos/hellenicdev/pebbles/releases", {
      headers: { accept: "application/vnd.github+json" }
    })
      .then(function (response) {
        if (!response.ok) throw new Error("status");
        return response.json();
      })
      .then(function (releases) {
        if (!Array.isArray(releases) || releases.length === 0) {
          status.textContent = "No releases yet. The build log will appear here as we ship.";
          return;
        }
        status.textContent = "";
        var formatter = new Intl.DateTimeFormat("en-GB", {
          day: "numeric",
          month: "long",
          year: "numeric",
          timeZone: "UTC"
        });
        releases.slice(0, 8).forEach(function (release) {
          var item = document.createElement("li");
          var link = document.createElement("a");
          link.href = safeReleaseUrl(release && release.html_url);
          var name = document.createElement("span");
          name.className = "release-name";
          name.textContent = String((release && (release.name || release.tag_name)) || "Release");
          var meta = document.createElement("span");
          meta.className = "release-meta";
          var when = release && (release.published_at || release.created_at);
          var metaText = when ? formatter.format(new Date(when)) : "Undated";
          if (release && release.prerelease) metaText += " · Pre-release";
          meta.textContent = metaText;
          link.appendChild(name);
          link.appendChild(meta);
          var blurb = firstLine(release && release.body);
          if (blurb) {
            var note = document.createElement("p");
            note.className = "release-blurb";
            note.textContent = blurb;
            link.appendChild(note);
          }
          item.appendChild(link);
          list.appendChild(item);
        });
      })
      .catch(function () {
        status.textContent = "The build log couldn't be loaded. Releases are also on GitHub.";
      });
  }

  function safeReleaseUrl(url) {
    if (typeof url === "string" && url.indexOf("https://github.com/hellenicdev/pebbles/") === 0) {
      return url;
    }
    return "https://github.com/hellenicdev/pebbles/releases";
  }

  function firstLine(body) {
    if (typeof body !== "string") return "";
    var parts = body.split("\n");
    var line = "";
    for (var i = 0; i < parts.length; i += 1) {
      if (parts[i].trim()) {
        line = parts[i].trim();
        break;
      }
    }
    if (!line) return "";
    var clean = line.replace(/^[#>*\-\s]+/, "").trim();
    if (!clean) return "";
    return clean.length > 160 ? clean.slice(0, 157) + "…" : clean;
  }
})();
