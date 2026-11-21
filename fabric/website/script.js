/* rtmu.net — ヘッダー状態 / スクロール連動アニメーション / バージョン自動表示 */
(function () {
  "use strict";

  /* ------------------------------------------------------------------
     1. ヘッダー: スクロールでガラス化
     ------------------------------------------------------------------ */
  var header = document.getElementById("site-header");
  function onScrollHeader() {
    if (!header) return;
    if (window.scrollY > 24) {
      header.classList.add("scrolled");
    } else {
      header.classList.remove("scrolled");
    }
  }
  window.addEventListener("scroll", onScrollHeader, { passive: true });
  onScrollHeader();

  /* ------------------------------------------------------------------
     2. スクロール連動リビール: [data-reveal] が見えたら .revealed
     ------------------------------------------------------------------ */
  var revealTargets = document.querySelectorAll("[data-reveal]");
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("revealed");
          io.unobserve(entry.target); // 一度出たら再アニメしない
        }
      });
    }, { threshold: 0.14, rootMargin: "0px 0px -40px 0px" });
    revealTargets.forEach(function (el) { io.observe(el); });
  } else {
    revealTargets.forEach(function (el) { el.classList.add("revealed"); });
  }

  /* ------------------------------------------------------------------
     3. ヒーローの軽いパララックス (スクロールで文字がゆっくり退く)
     ------------------------------------------------------------------ */
  var heroInner = document.querySelector(".hero-inner");
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (heroInner && !reduceMotion) {
    var ticking = false;
    window.addEventListener("scroll", function () {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(function () {
        var y = window.scrollY;
        if (y < window.innerHeight) {
          heroInner.style.transform = "translateY(" + y * 0.18 + "px)";
          heroInner.style.opacity = String(Math.max(0, 1 - y / (window.innerHeight * 0.85)));
        }
        ticking = false;
      });
    }, { passive: true });
  }

  /* ------------------------------------------------------------------
     4. バージョン自動表示: GitHub の最新リリースを .rtmu-version へ
        (失敗時は書いてある表記のまま)
     ------------------------------------------------------------------ */
  fetch("https://api.github.com/repos/325-Sunnygo/RealTrainModUnofficial/releases/latest")
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (json) {
      if (!json || !json.tag_name) return;
      var v = "v" + String(json.tag_name).replace(/^v/i, "");
      document.querySelectorAll(".rtmu-version").forEach(function (el) {
        el.textContent = v;
      });
    })
    .catch(function () { /* オフライン等: 既存表記のまま */ });
})();
