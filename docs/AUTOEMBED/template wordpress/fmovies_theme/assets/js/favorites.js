jQuery(function ($) {
  //const g = document.querySelectorAll("#controls .bookmark");
  const g = document.querySelectorAll("#controls .bookmark, .item .bookmark");
  if (null != typeof g && 0 < g.length) {
    function c(b, c) {
      b
        ? $(c).attr("data-bookmark", "Remove").text('Remove')
        : $(c).attr("data-bookmark", "Favorite").text('Favorite');
    }
    let d;
    for (
      localStorage.hasOwnProperty("favorite_movies")
        ? (d = JSON.parse(localStorage.favorite_movies))
        : (localStorage.setItem("favorite_movies", "[]"), (d = [])),
        i = 0;
      i < g.length;
      i++
    ) {
      const $ = g[i],
        b = $.id,
        f = $.classList;
      d.includes(b)
        ? (f.remove("inactive"), f.add("active"), c(!0, $))
        : c(!1, $),
        $.addEventListener("click", function () {
          f.toggle("inactive"),
            f.toggle("active"),
            d.includes(b)
              ? ((d = d.filter(function (c) {
                  return c !== b;
                })),
                c(!1, $))
              : (d.push(b), c(!0, $)),
            (localStorage.favorite_movies = JSON.stringify(d));
        });
    }
  }
});





