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

  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!reduceMotion) root.classList.add("js-motion");

  var progress = document.createElement("div");
  progress.className = "scroll-progress";
  progress.setAttribute("aria-hidden", "true");
  var progressFill = document.createElement("span");
  progress.appendChild(progressFill);
  document.body.appendChild(progress);

  var header = document.querySelector(".site-header");
  var stage = document.querySelector(".stone-stage");
  var navLinks = Array.prototype.filter.call(
    document.querySelectorAll(".nav-row a"),
    function (link) { return (link.getAttribute("href") || "").charAt(0) === "#"; }
  );
  var scrollQueued = false;

  function markNav() {
    var current = "";
    navLinks.forEach(function (link) {
      var section = document.getElementById(link.getAttribute("href").slice(1));
      if (section && section.getBoundingClientRect().top < 150) current = section.id;
    });
    navLinks.forEach(function (link) {
      if (current && link.getAttribute("href") === "#" + current) link.setAttribute("aria-current", "location");
      else link.removeAttribute("aria-current");
    });
  }

  function onScroll() {
    if (scrollQueued) return;
    scrollQueued = true;
    requestAnimationFrame(function () {
      scrollQueued = false;
      var y = window.scrollY || document.documentElement.scrollTop || 0;
      var max = document.documentElement.scrollHeight - window.innerHeight;
      progressFill.style.transform = "scaleX(" + (max > 0 ? Math.min(y / max, 1) : 0) + ")";
      if (header) header.classList.toggle("is-scrolled", y > 12);
      if (stage && !reduceMotion) {
        var tiltX = stage.getAttribute("data-tilt-x") || "0";
        var tiltY = stage.getAttribute("data-tilt-y") || "0";
        stage.style.transform = "translateY(" + Math.min(y * 0.07, 40) + "px) rotateX(" + tiltX + "deg) rotateY(" + tiltY + "deg)";
      }
      markNav();
    });
  }

  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  if (stage && !reduceMotion) {
    stage.addEventListener("pointermove", function (event) {
      var box = stage.getBoundingClientRect();
      var x = (event.clientX - box.left) / box.width - 0.5;
      var y = (event.clientY - box.top) / box.height - 0.5;
      stage.setAttribute("data-tilt-x", String((-y * 8).toFixed(2)));
      stage.setAttribute("data-tilt-y", String((x * 10).toFixed(2)));
      onScroll();
    });
    stage.addEventListener("pointerleave", function () {
      stage.setAttribute("data-tilt-x", "0");
      stage.setAttribute("data-tilt-y", "0");
      onScroll();
    });
  }

  var revealObserver = null;
  if (!reduceMotion && "IntersectionObserver" in window) {
    revealObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("is-in");
        revealObserver.unobserve(entry.target);
      });
    }, { threshold: 0.18, rootMargin: "0px 0px -8% 0px" });
    document.querySelectorAll(".reveal").forEach(function (el) { revealObserver.observe(el); });
  }

  setupBench();

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
          item.classList.add("reveal");
          list.appendChild(item);
          if (revealObserver) revealObserver.observe(item);
          else item.classList.add("is-in");
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

  function setupBench() {
    var you = document.querySelector("#pebble-you");
    var them = document.querySelector("#pebble-them");
    var status = document.querySelector("#touch-status");
    var fill = document.querySelector("#warmth-fill");
    var pair = document.querySelector("#pair");
    var dock = document.querySelector("#dock");
    var quietInput = document.querySelector("#quiet-hours");
    var themName = document.querySelector("#them-name");
    if (!you || !them || !status) return;

    var colour = "#e08a45";
    var person = "Sam";
    var quiet = false;
    var themTimer = 0;
    var fadeTimer = 0;
    var dockTimer = 0;
    var audioCtx = null;

    function paint() {
      if (pair) pair.style.setProperty("--glow-colour", colour);
      if (dock) dock.style.setProperty("--glow-colour", colour);
      you.style.setProperty("--glow-colour", colour);
      them.style.setProperty("--glow-colour", colour);
    }
    paint();

    bindRadios(document.querySelector(".swatches"), function (button) {
      colour = button.getAttribute("data-colour") || colour;
      paint();
      status.textContent = "Your colour is " + button.textContent.trim() + ".";
    });
    bindRadios(document.querySelector(".who"), function (button) {
      person = button.textContent.trim();
      if (themName) themName.textContent = person;
      status.textContent = "A touch will reach " + person + ".";
    });

    if (quietInput) {
      quietInput.addEventListener("change", function () {
        quiet = quietInput.checked;
        status.textContent = quiet ? "Quiet hours are on. Their Pebble stays still." : "Quiet hours are off.";
      });
    }

    armHold(you, function (held) { send(held ? "hug" : "tap"); });

    them.addEventListener("click", function () {
      if (quiet) {
        status.textContent = "Quiet hours. Nothing to answer.";
        return;
      }
      if (!them.classList.contains("is-lit")) {
        status.textContent = "Nothing is waiting. Touch yours first.";
        return;
      }
      them.classList.remove("is-lit", "is-warm");
      you.classList.add("is-lit");
      chime("ack");
      status.textContent = person + " touched back. Got it.";
      window.setTimeout(function () { you.classList.remove("is-lit"); }, 900);
    });

    if (dock) {
      dock.addEventListener("click", function () {
        dock.classList.add("is-charging");
        status.textContent = "On the dock. The ring stays lit while it charges.";
        window.clearTimeout(dockTimer);
        dockTimer = window.setTimeout(function () { dock.classList.remove("is-charging"); }, 4200);
      });
    }

    function send(kind) {
      window.clearTimeout(themTimer);
      window.clearTimeout(fadeTimer);
      you.classList.add("is-lit");
      you.classList.toggle("is-warm", kind === "hug");
      them.classList.remove("is-lit", "is-warm");
      fill.classList.remove("is-tap", "is-hug");
      window.requestAnimationFrame(function () {
        fill.classList.add(kind === "hug" ? "is-hug" : "is-tap");
      });
      if (quiet) {
        status.textContent = "Quiet hours. " + person + "'s Pebble stays still.";
        window.setTimeout(function () { you.classList.remove("is-lit", "is-warm"); }, 450);
        return;
      }
      if (pair) pair.classList.add("is-sending");
      chime(kind);
      if (navigator.vibrate) navigator.vibrate(kind === "hug" ? [16, 40, 22] : 14);
      status.textContent = kind === "hug"
        ? "A hug for " + person + ". Warmth is capped at 42°C."
        : "Thinking of " + person + ". About 38°C.";
      themTimer = window.setTimeout(function () {
        if (pair) pair.classList.remove("is-sending");
        you.classList.remove("is-lit", "is-warm");
        them.classList.add("is-lit");
        them.classList.toggle("is-warm", kind === "hug");
      }, kind === "hug" ? 720 : 420);
      fadeTimer = window.setTimeout(function () {
        them.classList.remove("is-lit", "is-warm");
        fill.classList.remove("is-tap", "is-hug");
      }, kind === "hug" ? 3400 : 2100);
    }

    function chime(kind) {
      var Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) return;
      if (!audioCtx) audioCtx = new Ctx();
      if (audioCtx.state === "suspended") audioCtx.resume();
      var now = audioCtx.currentTime;
      var notes = kind === "hug" ? [392, 494] : kind === "ack" ? [659] : [523];
      notes.forEach(function (freq, index) {
        var osc = audioCtx.createOscillator();
        var gain = audioCtx.createGain();
        osc.type = "sine";
        osc.frequency.value = freq;
        var start = now + index * 0.08;
        gain.gain.setValueAtTime(0.0001, start);
        gain.gain.exponentialRampToValueAtTime(0.04, start + 0.03);
        gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.45);
        osc.connect(gain);
        gain.connect(audioCtx.destination);
        osc.start(start);
        osc.stop(start + 0.5);
      });
    }
  }

  function bindRadios(container, onPick) {
    if (!container) return;
    var buttons = Array.prototype.slice.call(container.querySelectorAll("[role='radio']"));
    function choose(index) {
      buttons.forEach(function (button, i) {
        var on = i === index;
        button.classList.toggle("is-selected", on);
        button.setAttribute("aria-checked", on ? "true" : "false");
        button.tabIndex = on ? 0 : -1;
      });
      onPick(buttons[index]);
    }
    buttons.forEach(function (button, index) {
      button.tabIndex = button.classList.contains("is-selected") ? 0 : -1;
      button.addEventListener("click", function () { choose(index); });
      button.addEventListener("keydown", function (event) {
        var next = index;
        if (event.key === "ArrowRight" || event.key === "ArrowDown") next = (index + 1) % buttons.length;
        else if (event.key === "ArrowLeft" || event.key === "ArrowUp") next = (index - 1 + buttons.length) % buttons.length;
        else return;
        event.preventDefault();
        buttons[next].focus();
        choose(next);
      });
    });
  }

  function armHold(button, onFire) {
    var start = 0;
    var warmTimer = 0;
    var active = false;
    var HOLD = 480;
    function press() {
      if (active) return;
      active = true;
      start = performance.now();
      button.classList.add("is-pressing");
      warmTimer = window.setTimeout(function () { button.classList.add("is-warm"); }, HOLD);
    }
    function release() {
      if (!active) return;
      active = false;
      window.clearTimeout(warmTimer);
      var held = performance.now() - start >= HOLD;
      button.classList.remove("is-pressing");
      onFire(held);
    }
    button.addEventListener("pointerdown", function (event) {
      if (event.button !== 0) return;
      try { button.setPointerCapture(event.pointerId); } catch (err) { /* ignore */ }
      press();
    });
    button.addEventListener("pointerup", release);
    button.addEventListener("pointercancel", release);
    button.addEventListener("contextmenu", function (event) { event.preventDefault(); });
    button.addEventListener("keydown", function (event) {
      if (event.repeat) return;
      if (event.key !== " " && event.key !== "Enter") return;
      event.preventDefault();
      press();
    });
    button.addEventListener("keyup", function (event) {
      if (event.key !== " " && event.key !== "Enter") return;
      event.preventDefault();
      release();
    });
    button.addEventListener("blur", release);
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
