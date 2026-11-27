/* NovaOS website: quick fade-in while scrolling, glass navigation, light/dark switch, menu. */
(function () {
  "use strict";
  var root = document.documentElement;

  // fade elements in when they come into view (short and quick)
  var items = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          e.target.classList.add("in");
          io.unobserve(e.target);
        }
      });
    }, { rootMargin: "0px 0px -6% 0px", threshold: 0.08 });
    items.forEach(function (el, i) {
      el.style.transitionDelay = Math.min(i % 6, 5) * 55 + "ms";
      io.observe(el);
    });
  } else {
    items.forEach(function (el) { el.classList.add("in"); });
  }

  // navigation turns into glass after a little scrolling
  var nav = document.getElementById("nav");
  function onScroll() { nav.classList.toggle("scrolled", window.scrollY > 12); }
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  // light / dark: follows the system until the moon button is used
  var btn = document.getElementById("theme");
  btn.addEventListener("click", function () {
    var dark = root.dataset.theme ? root.dataset.theme === "dark"
      : !window.matchMedia("(prefers-color-scheme: light)").matches;
    root.dataset.theme = dark ? "light" : "dark";
    try { localStorage.setItem("nova-theme", root.dataset.theme); } catch (e) { /* private mode */ }
  });

  // menu on small screens
  var burger = document.getElementById("burger");
  var links = document.getElementById("links");
  burger.addEventListener("click", function () { links.classList.toggle("open"); });
  links.addEventListener("click", function () { links.classList.remove("open"); });
})();
