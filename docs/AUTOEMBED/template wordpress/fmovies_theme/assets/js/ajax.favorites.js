jQuery(function (a) {
  a.post(Favorites.ajax_url, {
    action: "display_fav_movies",
    favorite_movies_list: JSON.parse(localStorage.favorite_movies)
  })
    .done(function (b) {
      let c, g, h;
      b
        ? (a("#page-favorites").html(b + '<div class="clearfix"></div>'))
        : (a("#page-favorites").addClass(
            "font-weight-light empty-page alert alert-warning"
          ),
          a("#page-favorites").text(
            "It looks like you haven't hearted anything!"
          ));
          lazyload();
          
    })
    .fail(function () {
      a("#page-favorites").html(
        '<h3 class="alert alert-danger">An error occured, please reload the page.</h3>'
      );
    })
    .always(function () {});
});

