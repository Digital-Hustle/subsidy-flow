/* FE-UI-02: тема как у фронта. Во фрейме формы входа (SignIn фронта, тот же origin) — встроенный вид и тема родителя;
   отдельной страницей — сохранённый выбор фронта (localStorage subsidy.theme) или системная тема. */
(function () {
  var html = document.documentElement;
  var media = window.matchMedia("(prefers-color-scheme: dark)");
  var parentHtml = null;
  try { if (window.top !== window.self) parentHtml = window.parent.document.documentElement; } catch (e) { parentHtml = null; }
  var dark = function () {
    if (parentHtml) return parentHtml.classList.contains("dark");
    var mode = null;
    try { mode = localStorage.getItem("subsidy.theme"); } catch (e) { mode = null; }
    return mode === "dark" || ((mode === null || mode === "system") && media.matches);
  };
  var sync = function () { html.classList.toggle("d-dark", dark()); };
  if (parentHtml) {
    html.classList.add("d-embed");
    new MutationObserver(sync).observe(parentHtml, { attributes: true, attributeFilter: ["class"] });
  } else if (media.addEventListener) {
    media.addEventListener("change", sync);
  }
  sync();
})();
